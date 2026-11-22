package nbcore

import (
	"net/netip"
	"testing"

	"nextbox/internal/store"
)

func TestLANOutsideVPN(t *testing.T) {
	for _, ipv6 := range []bool{false, true} {
		s := store.DefaultSettings()
		s.TunEnabled = true
		s.EnableIPv6 = ipv6
		s.TunExclude = "203.0.113.0/24"
		cfg := buildAndroid(t, s, androidOptions{})
		var tun map[string]any
		for _, in := range cfg["inbounds"].([]any) {
			if m := in.(map[string]any); m["type"] == "tun" {
				tun = m
			}
		}
		var excl []netip.Prefix
		for _, s := range tun["route_exclude_address"].([]string) {
			excl = append(excl, netip.MustParsePrefix(s))
		}
		covered := func(a string) bool {
			for _, p := range excl {
				if p.Contains(netip.MustParseAddr(a)) {
					return true
				}
			}
			return false
		}
		for _, a := range []string{"192.168.1.1", "10.8.0.3", "172.16.0.1", "172.19.0.4", "172.31.255.255", "169.254.1.1", "239.255.255.250", "224.0.0.251", "255.255.255.255", "203.0.113.9"} {
			if !covered(a) {
				t.Errorf("ipv6=%v: %s stays in the VPN", ipv6, a)
			}
		}
		// the VPN's own addresses (and the DNS server in them) stay inside
		for _, a := range []string{"172.19.0.1", "172.19.0.2", "8.8.8.8", "198.18.0.1"} {
			if covered(a) {
				t.Errorf("ipv6=%v: %s left the VPN", ipv6, a)
			}
		}
		if ipv6 {
			for _, a := range []string{"fe80::1", "fd12::1", "ff02::fb"} {
				if !covered(a) {
					t.Errorf("%s stays in the VPN", a)
				}
			}
			if covered("fdfe:dcba:9876::2") || covered("2001:db8::1") {
				t.Error("the VPN's IPv6 address or a public one left the VPN")
			}
		} else if covered("fe80::1") {
			t.Error("IPv6 ranges without IPv6")
		}
	}
}

func TestWithout(t *testing.T) {
	got := without(netip.MustParsePrefix("172.16.0.0/12"), netip.MustParsePrefix("172.19.0.0/30"))
	total := 0
	for _, p := range got {
		if p.Overlaps(netip.MustParsePrefix("172.19.0.0/30")) {
			t.Fatalf("%s overlaps the kept range", p)
		}
		total += 1 << (32 - p.Bits())
	}
	if total != 1<<20-4 {
		t.Fatalf("covers %d addresses, want %d: %v", total, 1<<20-4, got)
	}
}
