package nbcore

import (
	"encoding/json"
	"fmt"
	"strings"
	"testing"

	"nextbox/internal/config"
	"nextbox/internal/store"
	"nextbox/internal/vault"
)

func TestClassify(t *testing.T) {
	for in, want := range map[string]string{
		"https://sub.example.com/api/v1/client/subscribe?token=abc":            "subscription",
		"clash://install-config?url=https%3A%2F%2Fa.com%2Fs&name=Airport":      "subscription",
		"sing-box://import-remote-profile?url=https%3A%2F%2Fa.com%2Fs#Airport": "subscription",
		"vless://uuid@1.2.3.4:443?security=tls#node":                           "nodes",
		"hello world": "none",
	} {
		var c classified
		json.Unmarshal([]byte(Classify(in)), &c)
		if c.Kind != want {
			t.Errorf("%s: kind %q, want %q", in, c.Kind, want)
		}
	}
	var c classified
	json.Unmarshal([]byte(Classify("clash://install-config?url=https%3A%2F%2Fa.com%2Fs&name=Airport")), &c)
	if c.URL != "https://a.com/s" || c.Name != "Airport" {
		t.Errorf("clash link: %+v", c)
	}
	json.Unmarshal([]byte(Classify("http://user:pass@1.2.3.4:8080")), &c)
	if !c.LikelyProxy {
		t.Error("an address with credentials and a port should look like a proxy")
	}
	c = classified{}
	json.Unmarshal([]byte(Classify("https://sub.example.com/link/abc")), &c)
	if c.LikelyProxy {
		t.Error("a subscription path is not a proxy")
	}
}

func TestShadowTLSExpand(t *testing.T) {
	p := store.Profile{ID: 1, Name: "st", Type: "shadowsocks", Outbound: map[string]any{
		"type": "shadowsocks", "server": "1.2.3.4", "server_port": 443, "method": "2022-blake3-aes-128-gcm", "password": "x",
		"shadowtls": map[string]any{"version": 3, "password": "y", "server_name": "a.com"},
	}}
	obs, err := config.Expand(p, "st", nil, map[string]bool{})
	if err != nil {
		t.Fatal(err)
	}
	if len(obs) != 2 || obs[0]["type"] != "shadowtls" || obs[1]["detour"] != obs[0]["tag"] || obs[1]["tag"] != "st" {
		t.Fatalf("unexpected outbounds: %v", obs)
	}
	if _, ok := obs[1]["shadowtls"]; ok {
		t.Fatal("the helper field must not reach sing-box")
	}
}

func TestPackageRules(t *testing.T) {
	s := store.DefaultSettings()
	s.TunEnabled = true
	s.RouteMode = "rule"
	s.Rules = []store.Rule{
		{Kind: "package", Values: []string{"cmb.pb"}, Target: store.TargetDirect},
		{Kind: "process", Values: []string{"chrome.exe"}, Target: store.TargetDirect},
	}
	cfg := buildAndroid(t, s, androidOptions{})
	b, _ := json.Marshal(cfg["route"])
	route := string(b)
	if !strings.Contains(route, `"package_name":["cmb.pb"]`) || strings.Contains(route, "chrome.exe") {
		t.Fatalf("package rule kept, program rule dropped: %s", route)
	}
	if !strings.Contains(route, `"find_process":true`) {
		t.Fatal("package rules need find_process")
	}
}

func TestBackupRoundTrip(t *testing.T) {
	if err := Open(t.TempDir()); err != nil {
		t.Fatal(err)
	}
	defer func() { mu.Lock(); st = nil; mu.Unlock() }()
	gid, _ := AddGroup("g")
	ImportText("ss://YWVzLTEyOC1nY206cGFzcw@1.2.3.4:8388#a\ntrojan://p@5.6.7.8:443#b", gid)

	out, err := ExportBackup("pw", `{"perAppList":["cmb.pb"]}`)
	if err != nil {
		t.Fatal(err)
	}
	if _, ok := vault.Parse([]byte(out)); !ok {
		t.Fatal("expected an encrypted backup")
	}
	info, _ := InspectBackup(out, "")
	if !strings.Contains(info, `"needPassword":true`) {
		t.Fatalf("inspect without password: %s", info)
	}
	info, err = InspectBackup(out, "pw")
	if err != nil || !strings.Contains(info, `"platform":"android"`) || !strings.Contains(info, `"nodes":2`) {
		t.Fatalf("inspect: %s %v", info, err)
	}
	res, err := ImportBackup(out, "pw", true)
	if err != nil || !strings.Contains(res, "cmb.pb") {
		t.Fatalf("import: %s %v", res, err)
	}

	// a desktop backup: its TUN / port settings must not come along
	win := `{"app":"NextBox","version":2,"platform":"windows","settings":{"routeMode":"global","mixedPort":2030,"tunEnabled":false,"theme":"dark","rules":[{"kind":"suffix","values":["a.com"],"target":"direct"},{"kind":"process","values":["chrome.exe"],"target":"proxy"}],"scenes":[{"id":"ai","enabled":true,"target":"proxy"}]}}`
	if _, err := ImportBackup(win, "", false); err != nil {
		t.Fatal(err)
	}
	s, _ := current()
	set := s.Settings()
	if set.MixedPort != 2080 || !set.TunEnabled || set.Theme != "dark" || set.RouteMode != "rule" || len(set.Rules) != 2 || set.Rules[0].Kind != "suffix" || set.Rules[1].Kind != "geosite" {
		t.Fatalf("settings after a desktop import: port %d tun %v theme %s mode %s rules %d", set.MixedPort, set.TunEnabled, set.Theme, set.RouteMode, len(set.Rules))
	}
}

func TestPathOrder(t *testing.T) {
	rules := []store.Rule{
		{Kind: "suffix", Values: []string{"proxy.example.com"}, Target: store.TargetProxy},
		{Kind: "suffix", Values: []string{"example.com", "other.org"}, Target: store.TargetDirect},
		{Kind: "keyword", Values: []string{"ads"}, Target: store.TargetBlock},
		{Kind: "domain", Values: []string{"x.proxy.example.com"}, Target: store.TargetDirect},
		{Kind: "package", Values: []string{"cmb.pb"}, Target: store.TargetDirect},
		{Kind: "suffix", Values: []string{"jp.example.net"}, Target: store.PolicyTarget(7)},
		{Kind: "suffix", Values: []string{"off.com"}, Target: store.TargetBlock, Disabled: true},
	}
	var got []string
	for _, r := range pathOrder(rules) {
		got = append(got, fmt.Sprintf("%s:%s:%s", r.Step, r.Kind, strings.Join(r.Values, ",")))
	}
	want := []string{
		"block:keyword:ads",
		// the proxy rule is more specific than example.com, so it moves up
		// to the direct step, after the even more specific direct domain
		"direct:domain:x.proxy.example.com",
		"direct:suffix:proxy.example.com",
		"direct:suffix:example.com,other.org",
		"direct:package:cmb.pb",
		"policy:suffix:jp.example.net",
	}
	if strings.Join(got, "\n") != strings.Join(want, "\n") {
		t.Fatalf("path order:\n%s\nwant:\n%s", strings.Join(got, "\n"), strings.Join(want, "\n"))
	}
	if r := pathOrder(rules)[2]; r.Target != store.TargetProxy || r.Orig != 0 {
		t.Fatalf("moved rule keeps its target: %+v", r)
	}
}

func TestTestRouteSteps(t *testing.T) {
	if err := Open(t.TempDir()); err != nil {
		t.Fatal(err)
	}
	defer func() { mu.Lock(); st = nil; mu.Unlock() }()
	SaveRules(`[{"kind":"suffix","values":["example.com"],"target":"direct"},{"kind":"suffix","values":["api.example.com"],"target":"proxy"},{"kind":"package","values":["cmb.pb"],"target":"block"}]`)
	for _, c := range []struct{ host, pkg, step, value string }{
		{"www.example.com", "", "direct", "example.com"},
		{"v1.api.example.com", "", "proxy", "api.example.com"}, // proxy rule, checked before example.com
		{"www.example.com", "cmb.pb", "block", "cmb.pb"},
		{"192.168.1.1", "", "lan", ""},
		{"example.org", "", "final", ""},
	} {
		out, err := TestRoute(t.TempDir(), c.host, c.pkg)
		if err != nil || !strings.Contains(out, `"step":"`+c.step+`"`) || !strings.Contains(out, `"value":"`+c.value+`"`) && c.value != "" {
			t.Errorf("%s %s: %s %v", c.host, c.pkg, out, err)
		}
	}
}

func TestDirectNodeServersIDN(t *testing.T) {
	cfg := map[string]any{"route": map[string]any{"rules": []any{map[string]any{"action": "sniff"}}}}
	directNodeServers(cfg, []store.Profile{{Outbound: map[string]any{"server": "sg1.例子.com"}}})
	rule := cfg["route"].(map[string]any)["rules"].([]any)[0].(map[string]any)
	if d := rule["domain"].([]string); len(d) != 2 || d[1] != "sg1.xn--fsqu00a.com" {
		t.Fatalf("domains: %v", d)
	}
}
