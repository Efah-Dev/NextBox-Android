package store

import (
	"strings"
	"testing"
)

func TestNaturalCompare(t *testing.T) {
	for _, c := range []struct{ a, b string }{
		{"日本2", "日本10"},
		{"🇯🇵 日本 02", "🇯🇵 日本 3"},
		{"🇸🇬 Singapore", "tokyo"}, // the flag is not compared, case is not
		{"node 9b", "Node 10a"},
		{"a1.example.com:443", "a12.example.com:443"},
		{"abc", "abcd"},
	} {
		if NaturalCompare(c.a, c.b) >= 0 || NaturalCompare(c.b, c.a) <= 0 {
			t.Errorf("%q should come before %q", c.a, c.b)
		}
	}
	if NaturalCompare("HK 01", "hk 1") != 0 {
		t.Error("equal names")
	}
}

// Sorting: three directions, untested and failed nodes last both ways, the
// sort kept by subscription updates and dropped by dragging.
func TestSortDirections(t *testing.T) {
	s := open(t, t.TempDir())
	g, _ := s.AddGroup(Group{Name: "sub", URL: "https://example.com/sub"})
	mk := func(name, typ string, latency int) Profile {
		p := node(name)
		p.Type, p.Latency = typ, latency
		return p
	}
	sub := []Profile{mk("n10", "vless", 80), mk("n2", "trojan", -1), mk("n1", "hysteria2", 0), mk("n3", "vless", 30)}
	s.ReplaceGroupProfiles(g.ID, sub, true)
	for i, p := range s.Profiles(g.ID) {
		s.SetLatency(p.ID, sub[i].Latency, "", 1, "url")
	}
	order := func() string {
		var b strings.Builder
		for _, p := range s.Profiles(g.ID) {
			b.WriteString(p.Name + " ")
		}
		return b.String()
	}
	for _, c := range []struct{ by, dir, want string }{
		{SortByLatency, SortAsc, "n3 n10 n1 n2 "},
		{SortByLatency, SortDesc, "n10 n3 n1 n2 "},
		{SortByName, SortAsc, "n1 n2 n3 n10 "},
		{SortByName, SortDesc, "n10 n3 n2 n1 "},
		{SortByType, SortAsc, "n1 n2 n10 n3 "},
		{SortByName, "", "n10 n2 n1 n3 "}, // the subscription's order
	} {
		if err := s.SortGroup(g.ID, c.by, c.dir); err != nil {
			t.Fatal(err)
		}
		if got := order(); got != c.want {
			t.Errorf("%s %s: %s, want %s", c.by, c.dir, got, c.want)
		}
	}
	if gr, _ := s.Group(g.ID); gr.SortBy != SortByName || gr.SortDir != "" {
		t.Errorf("remembered %q %q", gr.SortBy, gr.SortDir)
	}

	// an update of a sorted group sorts again
	s.SortGroup(g.ID, SortByName, SortAsc)
	s.ReplaceGroupProfiles(g.ID, append(sub, mk("n0", "vless", 0)), true)
	if got := order(); got != "n0 n1 n2 n3 n10 " {
		t.Errorf("after an update: %s", got)
	}
	// dragging makes it the user's own order
	ps := s.Profiles(g.ID)
	s.ReorderProfiles(g.ID, []int64{ps[4].ID})
	if gr, _ := s.Group(g.ID); gr.SortBy != "" || gr.SortDir != "" {
		t.Errorf("dragging kept %q %q", gr.SortBy, gr.SortDir)
	}
	// the choice after a test
	s.SortAfterTest(g.ID, SortByLatency)
	if gr, _ := s.Group(g.ID); gr.SortBy != SortByLatency || gr.SortDir != SortAsc {
		t.Errorf("after a test: %q %q", gr.SortBy, gr.SortDir)
	}
}
