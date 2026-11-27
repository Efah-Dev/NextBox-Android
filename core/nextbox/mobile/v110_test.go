package nbcore

import (
	"encoding/json"
	"fmt"
	"runtime"
	"strings"
	"testing"

	"nextbox/internal/config"
	"nextbox/internal/link"
	"nextbox/internal/store"
)

func TestClassify(t *testing.T) {
	for in, want := range map[string]string{
		"https://sub.example.com/api/v1/client/subscribe?token=abc":            "subscription",
		"clash://install-config?url=https%3A%2F%2Fa.com%2Fs&name=Airport":      "subscription",
		"sing-box://import-remote-profile?url=https%3A%2F%2Fa.com%2Fs#Airport": "subscription",
		"vless://uuid@1.2.3.4:443?security=tls#node":                           "nodes",
		"hello world": "none",
	} {
		var c link.Classified
		json.Unmarshal([]byte(Classify(in)), &c)
		if c.Kind != want {
			t.Errorf("%s: kind %q, want %q", in, c.Kind, want)
		}
	}
	var c link.Classified
	json.Unmarshal([]byte(Classify("clash://install-config?url=https%3A%2F%2Fa.com%2Fs&name=Airport")), &c)
	if c.URL != "https://a.com/s" || c.Name != "Airport" {
		t.Errorf("clash link: %+v", c)
	}
	json.Unmarshal([]byte(Classify("http://user:pass@1.2.3.4:8080")), &c)
	if !c.LikelyProxy {
		t.Error("an address with credentials and a port should look like a proxy")
	}
	c = link.Classified{}
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
	if strings.Contains(route, "chrome.exe") {
		t.Fatalf("program rule dropped: %s", route)
	}
	if runtime.GOOS != "android" {
		t.Skip("app rules are matched on Android only (see config.matcher)")
	}
	if !strings.Contains(route, `"package_name":["cmb.pb"]`) {
		t.Fatalf("package rule kept: %s", route)
	}
	if !strings.Contains(route, `"find_process":true`) {
		t.Fatal("package rules need find_process")
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

// The tester checks the steps in their stored order, like the core: the
// first rule that matches wins.
func TestTestRouteSteps(t *testing.T) {
	if err := Open(t.TempDir()); err != nil {
		t.Fatal(err)
	}
	defer func() { mu.Lock(); st = nil; mu.Unlock() }()
	SetRouteMode("rule")
	SaveRules(`[{"kind":"suffix","values":["example.com"],"target":"direct"},{"kind":"suffix","values":["api.example.com"],"target":"proxy"},{"kind":"package","values":["cmb.pb"],"target":"block"},
{"kind":"set","name":"set","target":"proxy","items":[{"kind":"keyword","values":["setword"]}],"values":[]}]`, "")
	for _, c := range []struct{ host, pkg, step, value string }{
		{"www.example.com", "", "rule", "example.com"},
		{"v1.api.example.com", "", "rule", "example.com"}, // the broader rule comes first
		{"www.setword.net", "", "rule", "setword"},
		{"192.168.1.1", "", "lan", ""},
		{"example.org", "", "final", ""},
	} {
		out, err := TestRoute(t.TempDir(), c.host, c.pkg)
		if err != nil || !strings.Contains(out, `"step":"`+c.step+`"`) || !strings.Contains(out, `"value":"`+c.value+`"`) && c.value != "" {
			t.Errorf("%s %s: %s %v", c.host, c.pkg, out, err)
		}
	}
}

// Rules of Android 1.x get the order the old path checked them in, so that
// the same traffic goes the same way after the update.
func TestMigrateRoute(t *testing.T) {
	if err := Open(t.TempDir()); err != nil {
		t.Fatal(err)
	}
	defer func() { mu.Lock(); st = nil; mu.Unlock() }()
	SaveRules(`[{"kind":"suffix","values":["example.com"],"target":"direct"},{"kind":"suffix","values":["api.example.com"],"target":"proxy"},
{"kind":"keyword","values":["ads"],"target":"block"},{"kind":"process","values":["x.exe"],"target":"direct"},{"kind":"suffix","values":["off.com"],"target":"block","disabled":true}]`, "")
	if err := MigrateRoute(false); err != nil {
		t.Fatal(err)
	}
	set := st.Settings()
	if set.RouteMode != "rule" {
		t.Fatalf("mode %s", set.RouteMode)
	}
	want := "ads:> | keyword:ads>block | suffix:api.example.com>proxy | suffix:example.com>direct | process:x.exe>direct | suffix:off.com>block | scene:ai> | scene:media> | scene:games> | scene:cn>"
	if got := ruleList(set.Rules); got != want {
		t.Fatalf("rules:\n%s\nwant:\n%s", got, want)
	}
	if out, _ := TestRoute(t.TempDir(), "v1.api.example.com", ""); !strings.Contains(out, `"target":"proxy"`) {
		t.Fatalf("api.example.com after the migration: %s", out)
	}
	if err := MigrateRoute(true); err != nil || st.Settings().RouteMode != "direct" {
		t.Fatal("all direct")
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
