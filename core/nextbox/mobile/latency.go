package nbcore

import (
	"context"
	"net"
	"sync"
	"time"

	"nextbox/internal/i18n"
	"nextbox/internal/store"
)

// LatencyHandler receives the progress of a latency test.
type LatencyHandler interface {
	OnStart(id int64)
	// OnResult reports a node's latency in ms, or -1 with a message.
	OnResult(id int64, latency int32, message string)
}

var (
	testMu     sync.Mutex
	testCancel context.CancelFunc
	testRun    int
)

// TCPPing measures the TCP handshake time to each node server (ids is a comma
// separated list). It blocks until every node is done or CancelTest is called;
// results are saved to the store. Chain proxies only get a URL test.
func TCPPing(ids string, h LatencyHandler) error {
	s, err := current()
	if err != nil {
		return err
	}
	ctx, cancel := context.WithCancel(context.Background())
	testMu.Lock()
	if testCancel != nil {
		testCancel()
	}
	testCancel = cancel
	testRun++
	run := testRun
	testMu.Unlock()
	defer func() {
		testMu.Lock()
		cancel()
		if testRun == run {
			testCancel = nil
		}
		testMu.Unlock()
	}()

	set := s.Settings()
	sem := make(chan struct{}, max(1, set.TestConcurrency))
	var wg sync.WaitGroup
	for _, n := range s.ProfilesByIDs(parseIDs(ids)) {
		if ctx.Err() != nil {
			break
		}
		sem <- struct{}{}
		wg.Add(1)
		go func(n store.Profile) {
			defer func() { <-sem; wg.Done() }()
			h.OnStart(n.ID)
			latency, msg := tcpPing(ctx, n, time.Duration(set.TestTimeout)*time.Millisecond)
			s.SetLatency(n.ID, latency, msg, time.Now().Unix(), "tcp")
			h.OnResult(n.ID, int32(latency), msg)
		}(n)
	}
	wg.Wait()
	return s.Flush()
}

func tcpPing(ctx context.Context, n store.Profile, timeout time.Duration) (int, string) {
	if n.IsChain() {
		return -1, i18n.T("链式代理只能用 URL 测试")
	}
	d := net.Dialer{Timeout: timeout}
	start := time.Now()
	conn, err := d.DialContext(ctx, "tcp", n.Server())
	if err != nil {
		if ne, ok := err.(net.Error); ok && ne.Timeout() {
			return -1, i18n.T("超时")
		}
		return -1, err.Error()
	}
	conn.Close()
	return max(1, int(time.Since(start).Milliseconds())), ""
}

// SetLatency records a URL test result (ms, or -1 with a message); call
// Flush afterwards to persist it.
func SetLatency(id int64, latency int32, message string) {
	if s, err := current(); err == nil {
		s.SetLatency(id, int(latency), message, time.Now().Unix(), "url")
	}
}

// CancelTest stops a running latency test.
func CancelTest() {
	testMu.Lock()
	defer testMu.Unlock()
	if testCancel != nil {
		testCancel()
	}
}
