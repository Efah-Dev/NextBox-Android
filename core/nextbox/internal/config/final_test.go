package config

import (
	"path/filepath"
	"testing"

	"nextbox/internal/ruleset"
	"nextbox/internal/store"
)

// The last step ("everything else") goes where the user sends it in rule
// mode; going direct, unmatched names are resolved by the direct DNS while
// the steps sending traffic to a node keep the remote DNS (and FakeIP).
func TestFinalTarget(t *testing.T) {
	bin := singBox(t)
	ruleDir := t.TempDir()
	for _, name := range []string{"geosite-cn", "geoip-cn", ForeignSites} {
		if err := ruleset.Install(name, filepath.Join(ruleDir, name+".srs")); err != nil {
			t.Fatal(err)
		}
	}
	nodes := sampleProfiles(t)
	s := store.DefaultSettings()
	s.RouteMode = "rule"
	s.TunEnabled, s.TunFakeIP = true, true
	s.Rules = []store.Rule{{Kind: "suffix", Values: []string{"google.com"}, Target: store.TargetProxy}}
	in := Input{
		Settings: s, Node: &nodes[0], ClashAPI: "127.0.0.1:9090", Secret: "x", RuleSetDir: ruleDir, SkipMissingRuleSets: true,
		Policies: []PolicyInput{{Policy: store.Policy{ID: 7, Name: "日本 自动", Mode: store.PolicyAuto}, Members: []store.Profile{nodes[1], nodes[2]}}},
	}
	build := func(mode, final string) (routeFinal, dnsFinal string, fakeCatchAll bool, cfg map[string]any) {
		t.Helper()
		in.Settings.RouteMode, in.Settings.FinalTarget = mode, final
		cfg, err := Build(in)
		if err != nil {
			t.Fatal(err)
		}
		check(t, bin, cfg)
		dns := cfg["dns"].(map[string]any)
		for _, r := range dns["rules"].([]any) {
			m := r.(map[string]any)
			if _, ok := m["query_type"]; ok && m["server"] == TagFakeIP && len(m) == 2 {
				fakeCatchAll = true
			}
		}
		return cfg["route"].(map[string]any)["final"].(string), dns["final"].(string), fakeCatchAll, cfg
	}

	if rf, df, fake, _ := build("rule", ""); rf != TagProxy || df != TagDNSRemote || !fake {
		t.Errorf("default: route %s, dns %s, fakeip catch-all %v", rf, df, fake)
	}
	rf, df, fake, cfg := build("rule", store.TargetDirect)
	if rf != TagDirect || df != TagDNSDirect || fake {
		t.Errorf("direct: route %s, dns %s, fakeip catch-all %v", rf, df, fake)
	}
	// google.com still goes through the node, resolved by FakeIP
	found := false
	for _, r := range cfg["dns"].(map[string]any)["rules"].([]any) {
		m := r.(map[string]any)
		if v, _ := m["domain_suffix"].([]string); len(v) == 1 && v[0] == "google.com" && m["server"] == TagFakeIP {
			found = true
		}
	}
	if !found {
		t.Error("the proxied rule lost its DNS rule")
	}
	if rf, _, _, _ := build("rule", store.PolicyTarget(7)); rf != "日本 自动" {
		t.Errorf("policy: route %s", rf)
	}
	if rf, df, _, _ := build("rule", store.PolicyTarget(99)); rf != TagProxy || df != TagDNSRemote {
		t.Errorf("missing policy: route %s, dns %s", rf, df)
	}
	// only rule mode has steps
	if rf, _, _, _ := build("global", store.TargetDirect); rf != TagProxy {
		t.Errorf("global: route %s", rf)
	}
	if rf, df, _, _ := build("direct", ""); rf != TagDirect || df != TagDNSDirect {
		t.Errorf("all direct: route %s, dns %s", rf, df)
	}
}
