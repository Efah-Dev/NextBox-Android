// Package nbtest measures real ("URL test") latency on Android: a temporary
// in-process sing-box instance exposes every node behind one local mixed
// inbound (see nbcore.BuildTestConfig), and an HTTP request is timed through
// each of them, the same way the Windows app does with a sing-box process.
package nbtest

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"errors"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/url"
	"strconv"
	"strings"
	"sync"
	"time"

	box "github.com/sagernet/sing-box"
	"github.com/sagernet/sing-box/include"
	"github.com/sagernet/sing-box/option"
	"github.com/sagernet/sing/common/json"

	nbcore "nextbox/mobile"
)

// LatencyHandler receives the progress of a test.
type LatencyHandler interface {
	OnStart(id int64)
	// OnResult reports a node's latency in ms, or -1 with a message.
	OnResult(id int64, latency int32, message string)
}

var (
	mu     sync.Mutex
	cancel context.CancelFunc
	run    int
)

// URLTest tests the given nodes (comma separated ids) against testURL. It
// blocks until done or Cancel; results are stored with nbcore.SetLatency.
func URLTest(ids, testURL string, timeoutMs, concurrency int32, h LatencyHandler) error {
	ctx, stop := context.WithCancel(context.Background())
	mu.Lock()
	if cancel != nil {
		cancel()
	}
	cancel = stop
	run++
	mine := run
	mu.Unlock()
	defer func() {
		mu.Lock()
		stop()
		if run == mine {
			cancel = nil
		}
		mu.Unlock()
	}()

	list := splitIDs(ids)
	if len(list) == 0 {
		return nil
	}
	timeout := time.Duration(max(1000, timeoutMs)) * time.Millisecond
	testBatches(ctx, list, testURL, timeout, int(max(1, concurrency)), h)
	return nbcore.Flush()
}

// Cancel stops a running URLTest.
func Cancel() {
	mu.Lock()
	defer mu.Unlock()
	if cancel != nil {
		cancel()
	}
}

// testBatches runs one instance for all nodes; if it refuses to start
// because a node config is invalid, the batch is split until the broken nodes
// are isolated.
func testBatches(ctx context.Context, ids []int64, testURL string, timeout time.Duration, concurrency int, h LatencyHandler) {
	if len(ids) == 0 || ctx.Err() != nil {
		return
	}
	err := testBatch(ctx, ids, testURL, timeout, concurrency, h)
	if err == nil || ctx.Err() != nil {
		return
	}
	if len(ids) == 1 {
		report(h, ids[0], -1, err.Error())
		return
	}
	mid := len(ids) / 2
	testBatches(ctx, ids[:mid], testURL, timeout, concurrency, h)
	testBatches(ctx, ids[mid:], testURL, timeout, concurrency, h)
}

func testBatch(ctx context.Context, ids []int64, testURL string, timeout time.Duration, concurrency int, h LatencyHandler) error {
	port, err := freePort()
	if err != nil {
		return err
	}
	secret := randomSecret()
	cfg, err := nbcore.BuildTestConfig(joinIDs(ids), int32(port), secret)
	if err != nil {
		return err
	}
	bctx := box.Context(context.Background(), include.InboundRegistry(), include.OutboundRegistry(),
		include.EndpointRegistry(), include.DNSTransportRegistry(), include.ServiceRegistry(), include.CertificateProviderRegistry())
	opts, err := json.UnmarshalExtendedContext[option.Options](bctx, []byte(cfg))
	if err != nil {
		return err
	}
	bctx, bcancel := context.WithCancel(bctx)
	defer bcancel()
	instance, err := box.New(box.Options{Context: bctx, Options: opts})
	if err != nil {
		return err
	}
	defer instance.Close()
	if err := instance.Start(); err != nil {
		return err
	}

	addr := net.JoinHostPort("127.0.0.1", strconv.Itoa(port))
	sem := make(chan struct{}, concurrency)
	var wg sync.WaitGroup
	for _, id := range ids {
		if ctx.Err() != nil {
			break
		}
		sem <- struct{}{}
		wg.Add(1)
		go func(id int64) {
			defer func() { <-sem; wg.Done() }()
			h.OnStart(id)
			proxy := &url.URL{Scheme: "http", User: url.UserPassword("n"+strconv.FormatInt(id, 10), secret), Host: addr}
			ms, err := measureRTT(ctx, proxy, testURL, timeout)
			if err != nil {
				report(h, id, -1, err.Error())
			} else {
				report(h, id, int32(ms), "")
			}
		}(id)
	}
	wg.Wait()
	return nil
}

func report(h LatencyHandler, id int64, ms int32, msg string) {
	nbcore.SetLatency(id, ms, msg)
	h.OnResult(id, ms, msg)
}

// measureRTT sends two requests over one kept-alive connection and times the
// second: the first one pays for the TCP / TLS / QUIC handshakes, the second
// shows the round trip a user feels on an open connection (like NekoBox).
func measureRTT(ctx context.Context, proxy *url.URL, testURL string, timeout time.Duration) (int, error) {
	tr := &http.Transport{
		Proxy:               http.ProxyURL(proxy),
		MaxIdleConnsPerHost: 1,
		ForceAttemptHTTP2:   true,
		TLSHandshakeTimeout: timeout,
	}
	defer tr.CloseIdleConnections()
	client := &http.Client{Transport: tr, Timeout: timeout}
	get := func() error {
		req, err := http.NewRequestWithContext(ctx, http.MethodGet, testURL, nil)
		if err != nil {
			return err
		}
		resp, err := client.Do(req)
		if err != nil {
			return err
		}
		io.Copy(io.Discard, io.LimitReader(resp.Body, 64<<10))
		resp.Body.Close()
		if resp.StatusCode >= 500 {
			return fmt.Errorf("HTTP %d", resp.StatusCode)
		}
		return nil
	}
	if err := get(); err != nil {
		return -1, simplify(err)
	}
	start := time.Now()
	if err := get(); err != nil {
		return -1, simplify(err)
	}
	return max(1, int(time.Since(start).Milliseconds())), nil
}

func simplify(err error) error {
	var ue *url.Error
	if errors.As(err, &ue) {
		err = ue.Err
	}
	if errors.Is(err, context.DeadlineExceeded) || strings.Contains(err.Error(), "Client.Timeout") {
		return errors.New("超时")
	}
	return err
}

func freePort() (int, error) {
	l, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		return 0, err
	}
	defer l.Close()
	return l.Addr().(*net.TCPAddr).Port, nil
}

func randomSecret() string {
	b := make([]byte, 16)
	rand.Read(b)
	return hex.EncodeToString(b)
}

func splitIDs(s string) []int64 {
	var out []int64
	for _, part := range strings.Split(s, ",") {
		if id, err := strconv.ParseInt(strings.TrimSpace(part), 10, 64); err == nil {
			out = append(out, id)
		}
	}
	return out
}

func joinIDs(ids []int64) string {
	parts := make([]string, len(ids))
	for i, id := range ids {
		parts[i] = strconv.FormatInt(id, 10)
	}
	return strings.Join(parts, ",")
}
