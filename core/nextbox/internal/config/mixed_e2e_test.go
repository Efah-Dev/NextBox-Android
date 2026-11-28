package config

import (
	"context"
	"io"
	"net"
	"net/http"
	"net/http/httptest"
	"net/url"
	"strconv"
	"testing"
	"time"

	"golang.org/x/net/proxy"

	"nextbox/internal/store"
)

// The mixed inbound every application talks to (system proxy) answers plain
// HTTP proxy requests, HTTPS through CONNECT and SOCKS5, with a config as
// NextBox builds it.
func TestMixedInboundEndToEnd(t *testing.T) {
	bin := singBox(t)
	web := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) { io.WriteString(w, "plain") }))
	defer web.Close()
	tlsWeb := httptest.NewTLSServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) { io.WriteString(w, "tls") }))
	defer tlsWeb.Close()

	s := store.DefaultSettings()
	s.RouteMode = "direct" // the local servers are reached direct in every mode
	s.MixedPort = freePort(t)
	cfg, err := Build(Input{Settings: s, RuleSetDir: t.TempDir()})
	if err != nil {
		t.Fatal(err)
	}
	delete(cfg, "experimental")
	stop := runCore(t, bin, cfg, s.MixedPort)
	defer stop()
	addr := "127.0.0.1:" + strconv.Itoa(s.MixedPort)

	get := func(name string, c *http.Client, u, want string) {
		t.Helper()
		resp, err := c.Get(u)
		if err != nil {
			t.Fatalf("%s: %v", name, err)
		}
		b, _ := io.ReadAll(resp.Body)
		resp.Body.Close()
		if string(b) != want {
			t.Errorf("%s: got %q", name, b)
		}
	}
	httpProxy := &http.Transport{Proxy: http.ProxyURL(&url.URL{Scheme: "http", Host: addr}), TLSClientConfig: tlsWeb.Client().Transport.(*http.Transport).TLSClientConfig}
	c := &http.Client{Timeout: 10 * time.Second, Transport: httpProxy}
	get("http proxy", c, web.URL, "plain")
	get("connect", c, tlsWeb.URL, "tls")

	d, err := proxy.SOCKS5("tcp", addr, nil, proxy.Direct)
	if err != nil {
		t.Fatal(err)
	}
	socks := &http.Transport{DialContext: func(ctx context.Context, network, a string) (net.Conn, error) { return d.Dial(network, a) }}
	get("socks5", &http.Client{Timeout: 10 * time.Second, Transport: socks}, web.URL, "plain")
}
