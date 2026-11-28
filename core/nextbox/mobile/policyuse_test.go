package nbcore

import (
	"encoding/json"
	"testing"
	"time"

	"nextbox/internal/config"
	"nextbox/internal/store"
)

// A policy group used for the connection becomes what "proxy" points at,
// and PolicyRuntime names the same outbounds as the config.
func TestUsePolicy(t *testing.T) {
	s := openTemp(t)
	g, _ := s.AddGroup(store.Group{Name: "g"})
	node := func(name, host string) store.Profile {
		return store.Profile{Name: name, Type: "trojan", Outbound: map[string]any{"type": "trojan", "server": host, "server_port": 443, "password": "p"}}
	}
	added, err := s.AddProfiles(g.ID, []store.Profile{node("JP", "jp.example"), node("US", "us.example")})
	if err != nil {
		t.Fatal(err)
	}
	p, _ := s.SavePolicy(store.Policy{Name: "auto", Match: store.PolicyManual, Members: []int64{added[0].ID, added[1].ID}, Mode: store.PolicyAuto, Interval: 3})
	if PolicyRuntime() != "" {
		t.Error("runtime without a policy group in use")
	}
	if err := SetSelectedPolicy(p.ID); err != nil {
		t.Fatal(err)
	}
	js, err := BuildConfig(t.TempDir(), "")
	if err != nil {
		t.Fatal(err)
	}
	var cfg map[string]any
	json.Unmarshal([]byte(js), &cfg)
	var rt policyRuntime
	if err := json.Unmarshal([]byte(PolicyRuntime()), &rt); err != nil || rt.Tag == "" || len(rt.Nodes) != 2 {
		t.Fatalf("runtime: %+v %v", rt, err)
	}
	found := false
	for _, ob := range cfg["outbounds"].([]any) {
		m := ob.(map[string]any)
		switch m["tag"] {
		case config.TagProxy:
			if m["default"] != rt.Tag {
				t.Errorf("proxy points at %v, not %s", m["default"], rt.Tag)
			}
		case rt.Tag:
			found = true
			// tested on NextBox's clock at the group's interval
			if m["type"] != "urltest" || m["interval"] != "24h" {
				t.Errorf("group: %v", m)
			}
			if g := autoGroups; len(g) != 1 || g[0].Tag != rt.Tag || g[0].Every != 3*time.Minute {
				t.Errorf("auto test groups: %+v", g)
			}
			for _, member := range m["outbounds"].([]any) {
				if rt.Nodes[member.(string)] == 0 {
					t.Errorf("member %v not in the runtime", member)
				}
			}
		}
	}
	if !found {
		t.Error("the policy group is not in the config")
	}
	var st state
	sj, _ := State()
	json.Unmarshal([]byte(sj), &st)
	if st.SelectedPolicy != p.ID {
		t.Errorf("state: %d", st.SelectedPolicy)
	}
}
