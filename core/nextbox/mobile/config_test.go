package nbcore

import (
	"encoding/json"
	"strings"
	"testing"

	"nextbox/internal/config"
	"nextbox/internal/store"
)

func buildAndroid(t *testing.T, s store.Settings, opt androidOptions) map[string]any {
	t.Helper()
	node := &store.Profile{ID: 1, Name: "n", Type: "vless", Outbound: map[string]any{
		"type": "vless", "server": "node.example.com", "server_port": 443, "uuid": "u",
	}}
	cfg, err := config.Build(config.Input{Settings: s, Node: node, RuleSetDir: t.TempDir(), SkipMissingRuleSets: true})
	if err != nil {
		t.Fatal(err)
	}
	adaptForAndroid(cfg, s, opt)
	return cfg
}

func inboundTypes(cfg map[string]any) []string {
	var out []string
	for _, in := range cfg["inbounds"].([]any) {
		out = append(out, in.(map[string]any)["type"].(string))
	}
	return out
}

func TestAndroidVPNMode(t *testing.T) {
	s := store.DefaultSettings()
	s.TunEnabled = true
	s.RouteMode = "global"
	cfg := buildAndroid(t, s, androidOptions{FakeIP: true})

	if got := strings.Join(inboundTypes(cfg), ","); got != "tun" {
		t.Fatalf("VPN mode must not open the local proxy port, inbounds = %s", got)
	}
	tun := cfg["inbounds"].([]any)[0].(map[string]any)
	if tun["stack"] != "gvisor" || tun["interface_name"] != nil || tun["strict_route"] != nil {
		t.Fatalf("tun inbound not adapted: %v", tun)
	}
	if _, ok := cfg["experimental"].(map[string]any)["clash_api"]; ok {
		t.Fatal("clash API must be removed")
	}
	b, _ := json.Marshal(cfg["dns"])
	if !strings.Contains(string(b), `"dns-fakeip"`) || strings.Contains(string(b), `"AAAA"`) {
		t.Fatalf("expected an IPv4-only fake-ip rule, dns = %s", b)
	}
	if cfg["log"].(map[string]any)["level"] != "warn" {
		t.Fatal("default log level should be warn")
	}
}

func TestAndroidLocalPortKept(t *testing.T) {
	s := store.DefaultSettings()
	s.TunEnabled = false // proxy-only mode
	if got := strings.Join(inboundTypes(buildAndroid(t, s, androidOptions{})), ","); got != "mixed" {
		t.Fatalf("proxy-only mode needs the mixed port, inbounds = %s", got)
	}
	s.TunEnabled = true
	s.AllowLAN = true
	if got := strings.Join(inboundTypes(buildAndroid(t, s, androidOptions{})), ","); got != "mixed,tun" {
		t.Fatalf("allow-LAN keeps the mixed port, inbounds = %s", got)
	}
}

func TestNoFakeIPWhenDirect(t *testing.T) {
	s := store.DefaultSettings()
	s.TunEnabled = true
	s.RouteMode = "direct"
	b, _ := json.Marshal(buildAndroid(t, s, androidOptions{FakeIP: true})["dns"])
	if strings.Contains(string(b), "fakeip") {
		t.Fatalf("direct mode must not use fake IPs: %s", b)
	}
}

func TestDirectNodeServers(t *testing.T) {
	cfg := map[string]any{"route": map[string]any{"rules": []any{
		map[string]any{"action": "sniff"},
		map[string]any{"action": "hijack-dns", "protocol": "dns"},
		map[string]any{"ip_is_private": true, "outbound": "direct"},
	}}}
	directNodeServers(cfg, []store.Profile{
		{Outbound: map[string]any{"server": "a.example.com"}},
		{Outbound: map[string]any{"server": "a.example.com"}},
		{Outbound: map[string]any{"server": "1.2.3.4"}},
		{Outbound: map[string]any{"server": "2001:db8::1"}},
	})
	rules := cfg["route"].(map[string]any)["rules"].([]any)
	if len(rules) != 4 {
		t.Fatalf("want 4 rules, got %d", len(rules))
	}
	r := rules[2].(map[string]any)
	b, _ := json.Marshal(r)
	want := `{"domain":["a.example.com"],"ip_cidr":["1.2.3.4/32","2001:db8::1/128"],"outbound":"direct"}`
	if string(b) != want {
		t.Fatalf("rule = %s, want %s", b, want)
	}
}
