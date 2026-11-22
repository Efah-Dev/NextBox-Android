package nbcore

import "net/netip"

// LAN and local addresses stay out of the VPN: the phone reaches them over
// Wi-Fi as without NextBox. Through the VPN the core sent them out again,
// which works for a plain TCP connection to a LAN IP but not for broadcast
// and multicast discovery (casting, file sharing, smart home), IPv6
// link-local addresses or LAN devices connecting to the phone: those timed
// out on wlan0 and the apps retried endlessly.
var lanRanges4 = []string{
	"10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16",
	"169.254.0.0/16",     // link-local
	"224.0.0.0/4",        // multicast
	"255.255.255.255/32", // broadcast
}

var lanRanges6 = []string{"fc00::/7", "fe80::/10", "ff00::/8"}

// excludeLAN adds the LAN ranges to the TUN inbound's route_exclude_address,
// leaving out the VPN's own addresses (172.19.0.0/30 is inside 172.16.0.0/12,
// the IPv6 one inside fc00::/7): the DNS server the phone is given lives
// there.
func excludeLAN(tun map[string]any, ipv6 bool) {
	var own []netip.Prefix
	addrs, _ := tun["address"].([]string)
	for _, a := range addrs {
		if p, err := netip.ParsePrefix(a); err == nil {
			own = append(own, p.Masked())
		}
	}
	ranges := lanRanges4
	if ipv6 {
		ranges = append(append([]string{}, lanRanges4...), lanRanges6...)
	}
	var out []string
	if prev, ok := tun["route_exclude_address"].([]string); ok {
		out = append(out, prev...)
	}
	seen := map[string]bool{}
	for _, s := range out {
		seen[s] = true
	}
	for _, r := range ranges {
		parts := []netip.Prefix{netip.MustParsePrefix(r)}
		for _, keep := range own {
			var next []netip.Prefix
			for _, p := range parts {
				next = append(next, without(p, keep)...)
			}
			parts = next
		}
		for _, p := range parts {
			if s := p.String(); !seen[s] {
				seen[s] = true
				out = append(out, s)
			}
		}
	}
	tun["route_exclude_address"] = out
}

// without returns p minus keep, as the fewest prefixes.
func without(p, keep netip.Prefix) []netip.Prefix {
	if !p.Overlaps(keep) {
		return []netip.Prefix{p}
	}
	if keep.Bits() <= p.Bits() {
		return nil // keep covers p
	}
	bits := p.Bits() + 1
	lo := netip.PrefixFrom(p.Addr(), bits)
	hiAddr := p.Addr().AsSlice()
	hiAddr[(bits-1)/8] |= 0x80 >> ((bits - 1) % 8)
	hi, _ := netip.AddrFromSlice(hiAddr)
	return append(without(lo, keep), without(netip.PrefixFrom(hi, bits), keep)...)
}
