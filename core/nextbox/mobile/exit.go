package nbcore

// Where the traffic of the connected node leaves, for the home page: the
// exit IP, its country and network, and the latency, as on the desktop.
//
// The requests go through a local inbound of their own (random port and
// password, made by BuildConfig): with "only the selected apps" NextBox itself
// may not be in the VPN, and a check outside it would see the phone's own IP.

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"errors"
	"net"
	"net/url"
	"strconv"
	"sync"
	"time"

	"nextbox/internal/geo"
	"nextbox/internal/i18n"
)

const probeUser = "nextbox"

var probe struct {
	sync.Mutex
	port   int
	secret string
}

// probeInbound is a mixed inbound on a free loopback port, with a new
// password each time the config is built.
func probeInbound() map[string]any {
	port := 0
	if l, err := net.Listen("tcp", "127.0.0.1:0"); err == nil {
		port = l.Addr().(*net.TCPAddr).Port
		l.Close()
	}
	b := make([]byte, 16)
	rand.Read(b)
	probe.Lock()
	probe.port, probe.secret = port, hex.EncodeToString(b)
	secret := probe.secret
	probe.Unlock()
	return map[string]any{
		"type": "mixed", "tag": "probe-in", "listen": "127.0.0.1", "listen_port": port,
		"users": []any{map[string]any{"username": probeUser, "password": secret}},
	}
}

type exitInfo struct {
	IP      string `json:"ip"`
	Country string `json:"country"`
	ASN     int    `json:"asn,omitempty"`
	Org     string `json:"org,omitempty"`
	Latency int    `json:"latency"` // ms; -1 failed
	Error   string `json:"error,omitempty"`
	// Failed is the request that failed: geo.ProbeStepTest (the test URL)
	// or geo.ProbeStepTrace (Cloudflare).
	Failed string `json:"failed,omitempty"`
}

// CheckExit measures a request through the running core and asks where it
// leaves (JSON exitInfo). It takes a few seconds; call it off the main thread.
func CheckExit() (string, error) {
	s, err := current()
	if err != nil {
		return "", err
	}
	probe.Lock()
	port, secret := probe.port, probe.secret
	probe.Unlock()
	if port == 0 {
		return "", errors.New(i18n.T("未连接"))
	}
	set := s.Settings()
	proxy := &url.URL{Scheme: "http", User: url.UserPassword(probeUser, secret), Host: "127.0.0.1:" + strconv.Itoa(port)}
	timeout := time.Duration(max(set.TestTimeout, 3000)) * time.Millisecond
	// two requests to the test URL, the trace and its fallback, then the ASN
	ctx, cancel := context.WithTimeout(context.Background(), 4*timeout+5*time.Second)
	defer cancel()
	ms, loc, err := geo.ProbeExit(ctx, proxy, set.TestURL, timeout)
	e := exitInfo{Latency: ms, IP: loc.IP, Country: loc.Country}
	if err != nil {
		e.Latency, e.Error, e.Failed = -1, err.Error(), geo.ProbeStepTest
		var pe *geo.ProbeError
		if errors.As(err, &pe) {
			e.Failed = pe.Step
		}
	} else if n, err := cachedNetwork(ctx, proxy, loc.IP, timeout); err == nil {
		e.ASN, e.Org = n.ASN, n.Org
	}
	b, err := json.Marshal(e)
	return string(b), err
}

// The network of an exit IP rarely changes: kept for a day.
var asnCache = struct {
	sync.Mutex
	m map[string]asnEntry
}{m: map[string]asnEntry{}}

type asnEntry struct {
	n  geo.Network
	at time.Time
}

func cachedNetwork(ctx context.Context, proxy *url.URL, ip string, timeout time.Duration) (geo.Network, error) {
	asnCache.Lock()
	c, ok := asnCache.m[ip]
	asnCache.Unlock()
	if ok && time.Since(c.at) < 24*time.Hour {
		return c.n, nil
	}
	n, err := geo.ExitNetwork(ctx, proxy, ip, timeout)
	if err != nil {
		return n, err
	}
	asnCache.Lock()
	if len(asnCache.m) > 200 {
		asnCache.m = map[string]asnEntry{}
	}
	asnCache.m[ip] = asnEntry{n, time.Now()}
	asnCache.Unlock()
	return n, nil
}
