package config

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"net/url"
	"strconv"
	"sync/atomic"
	"testing"
	"time"

	"nextbox/internal/link"
	"nextbox/internal/store"
)

// An automatic policy group runs with the core's own interval off, and a
// test asked for through the clash API (what NextBox's clock does) tests
// every member and lets the group pick one.
func TestAutoGroupTestedOnRequest(t *testing.T) {
	bin := singBox(t)
	var hits atomic.Int32
	web := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		hits.Add(1)
		w.WriteHeader(http.StatusNoContent)
	}))
	defer web.Close()

	// the nodes: two names for one local SOCKS server
	socksPort := freePort(t)
	stopSocks := runCore(t, bin, map[string]any{
		"log":       map[string]any{"level": "error"},
		"inbounds":  []any{map[string]any{"type": "socks", "listen": "127.0.0.1", "listen_port": socksPort}},
		"outbounds": []any{map[string]any{"type": "direct"}},
	}, socksPort)
	defer stopSocks()
	var nodes []store.Profile
	for i, name := range []string{"a", "b"} {
		n, err := link.Parse("socks5://127.0.0.1:" + strconv.Itoa(socksPort) + "#" + name)
		if err != nil {
			t.Fatal(err)
		}
		b, _ := json.Marshal(n.Outbound)
		var ob map[string]any
		json.Unmarshal(b, &ob)
		nodes = append(nodes, store.Profile{ID: int64(i + 1), Name: name, Type: n.Type(), Outbound: ob})
	}

	s := store.DefaultSettings()
	s.RouteMode = "global"
	s.TestURL = web.URL
	s.MixedPort = freePort(t)
	apiPort := freePort(t)
	in := Input{
		Settings: s, Node: &nodes[0], ClashAPI: "127.0.0.1:" + strconv.Itoa(apiPort), Secret: "secret", RuleSetDir: t.TempDir(),
		Policies: []PolicyInput{{Policy: store.Policy{ID: 5, Name: "auto", Mode: store.PolicyAuto, Interval: 1}, Members: nodes}},
	}
	cfg, err := Build(in)
	if err != nil {
		t.Fatal(err)
	}
	groups := AutoTestGroups(in, cfg)
	if len(groups) != 1 || groups[0].Every != time.Minute {
		t.Fatalf("groups = %+v", groups)
	}
	// no cache file: a fresh core every run
	cfg["experimental"] = map[string]any{"clash_api": map[string]any{"external_controller": in.ClashAPI, "secret": "secret"}}
	stop := runCore(t, bin, cfg, apiPort)
	defer stop()

	// the core tests the group once when it starts; what counts is the test asked for
	time.Sleep(1500 * time.Millisecond)
	before := hits.Load()
	req, _ := http.NewRequest(http.MethodGet, "http://"+in.ClashAPI+"/group/"+url.PathEscape(groups[0].Tag)+"/delay?timeout=5000", nil)
	req.Header.Set("Authorization", "Bearer secret")
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		t.Fatal(err)
	}
	defer resp.Body.Close()
	var delays map[string]int
	if err := json.NewDecoder(resp.Body).Decode(&delays); err != nil || resp.StatusCode != http.StatusOK {
		t.Fatalf("group test: %d %v", resp.StatusCode, err)
	}
	if len(delays) != 2 {
		t.Errorf("delays = %v", delays)
	}
	if hits.Load()-before < 2 {
		t.Errorf("the test URL was fetched %d times for the request", hits.Load()-before)
	}
}
