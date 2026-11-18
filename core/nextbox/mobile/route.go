package nbcore

// Routing as NextBox for Windows has it (2.0): the routing mode (global,
// rule, direct), and in rule mode the steps of the route in the order the
// user gives them — ad blocking, the scenes, rules and rule sets — between
// the fixed first step (LAN addresses) and the fixed last one (everything
// else). The rules, their order and the config built from them are the
// shared ones (internal/store, internal/config), so the two apps route the
// same way and backups move between them unchanged.

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"net"
	"path/filepath"
	"regexp"
	"strings"
	"sync"
	"syscall"
	"time"

	"nextbox/internal/config"
	"nextbox/internal/i18n"
	"nextbox/internal/store"
)

// SetRouteMode sets the routing mode: "global", "rule" or "direct".
func SetRouteMode(mode string) error {
	s, err := current()
	if err != nil {
		return err
	}
	switch mode {
	case "global", "rule", "direct":
	default:
		return fmt.Errorf("unknown routing mode %q", mode)
	}
	set := s.Settings()
	set.RouteMode = mode
	return s.SaveSettings(set)
}

// SaveRules replaces the rules (JSON list of store.Rule as State gives them,
// in the order they are checked, the places of ad blocking and the scenes
// included) and the scenes (JSON list of store.Scene; "" keeps them).
func SaveRules(js, scenes string) error {
	s, err := current()
	if err != nil {
		return err
	}
	var rules []store.Rule
	if err := json.Unmarshal([]byte(js), &rules); err != nil {
		return err
	}
	for i := range rules {
		if rules[i].IsStep() { // a place only: as store.placeSteps writes it
			rules[i].Target, rules[i].Disabled = "", false
		}
	}
	set := s.Settings()
	set.Rules = rules
	if scenes != "" {
		var sc []store.Scene
		if err := json.Unmarshal([]byte(scenes), &sc); err != nil {
			return err
		}
		set.Scenes = sc
	}
	return s.SaveSettings(set)
}

// ---------------------------------------------------------------- protected sockets

// SocketProtector keeps a socket out of the VPN (VpnService.protect). The
// app runs inside its own VPN, so the route tester's DNS lookups would
// otherwise be answered by the core (FakeIP addresses for proxied domains).
type SocketProtector interface {
	Protect(fd int32) bool
}

var (
	protectorMu sync.Mutex
	protector   SocketProtector
)

// SetSocketProtector is called by the VPN service when it starts (and with
// nil when it stops).
func SetSocketProtector(p SocketProtector) {
	protectorMu.Lock()
	protector = p
	protectorMu.Unlock()
}

func protectedDialer() *net.Dialer {
	return &net.Dialer{Timeout: 4 * time.Second, Control: func(_, _ string, c syscall.RawConn) error {
		protectorMu.Lock()
		p := protector
		protectorMu.Unlock()
		if p == nil {
			return nil
		}
		return c.Control(func(fd uintptr) { p.Protect(int32(fd)) })
	}}
}

// resolveDirect looks a name up with the direct DNS when that is plain DNS
// (an address, udp:// or tcp://), with 223.5.5.5 otherwise, outside the VPN.
func resolveDirect(directDNS, host string) []net.IP {
	addr := strings.TrimSpace(directDNS)
	network := "udp"
	switch {
	case strings.HasPrefix(addr, "udp://"):
		addr = strings.TrimPrefix(addr, "udp://")
	case strings.HasPrefix(addr, "tcp://"):
		addr, network = strings.TrimPrefix(addr, "tcp://"), "tcp"
	case strings.Contains(addr, "://"), addr == "", strings.EqualFold(addr, "local"), strings.EqualFold(addr, "dhcp"):
		addr = "223.5.5.5"
	}
	if _, _, err := net.SplitHostPort(addr); err != nil {
		addr = net.JoinHostPort(strings.Trim(addr, "[]"), "53")
	}
	r := &net.Resolver{PreferGo: true, Dial: func(ctx context.Context, _, _ string) (net.Conn, error) {
		return protectedDialer().DialContext(ctx, network, addr)
	}}
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	ips, err := r.LookupIP(ctx, "ip4", host)
	if err != nil {
		return nil
	}
	return ips
}

// ---------------------------------------------------------------- tester

// RuleSetMatcher answers whether a rule-set file matches a domain or IP. The
// sing-box side (package nbtest) installs it; without it rule-sets are skipped.
var (
	ruleSetMatcherMu sync.Mutex
	ruleSetMatcher   func(path, host string) (bool, error)
)

func SetRuleSetMatcherFunc(f func(path, host string) (bool, error)) {
	ruleSetMatcherMu.Lock()
	ruleSetMatcher = f
	ruleSetMatcherMu.Unlock()
}

type routeMatch struct {
	Input string `json:"input"`
	// Step is "lan", "ads", "rule" (Rule is its index in Settings.Rules),
	// "scene" (Scene) or "final".
	Step   string `json:"step"`
	Rule   int    `json:"rule"`
	Scene  string `json:"scene,omitempty"`
	Target string `json:"target"` // store.Target*, a policy target, or "final"
	Kind   string `json:"kind,omitempty"`
	Value  string `json:"value,omitempty"` // what matched (a domain, rule-set, package ...)
	// Resolved is the address the domain was resolved to before the steps
	// that match IP addresses, as the core does.
	Resolved string `json:"resolved,omitempty"`
	Note     string `json:"note,omitempty"`
}

var hostOnly = regexp.MustCompile(`^[a-z0-9.\-_:\[\]]+$`)

// TestRoute tells which step of the route a website or IP (and optionally
// the app making the connection, pkg) leaves at.
func TestRoute(ruleSetDir, input, pkg string) (string, error) {
	s, err := current()
	if err != nil {
		return "", err
	}
	host := strings.ToLower(strings.TrimSpace(input))
	host = strings.TrimPrefix(strings.TrimPrefix(host, "https://"), "http://")
	if i := strings.IndexAny(host, "/?#"); i >= 0 {
		host = host[:i]
	}
	if h, _, err := net.SplitHostPort(host); err == nil {
		host = h
	}
	host = strings.Trim(host, "[]")
	if host == "" || !hostOnly.MatchString(host) {
		return "", errors.New(i18n.T("请输入网址、域名或 IP 地址"))
	}
	res := routeMatch{Input: host, Rule: -1}
	set := phoneSettings(s.Settings())
	ip := net.ParseIP(host)
	if ip != nil && (ip.IsPrivate() || ip.IsLoopback() || ip.IsLinkLocalUnicast()) {
		res.Step, res.Target = stepLAN, store.TargetDirect
		return marshal(res)
	}
	// rule-sets added since the last connection are fetched now; what is still
	// missing cannot be judged and is named in the result
	ensureRuleSets(ruleSetDir, set, "")
	var missing []string
	for _, name := range config.MissingRuleSets(ruleSetDir, config.RequiredRuleSets(set)) {
		missing = append(missing, strings.TrimPrefix(strings.TrimPrefix(name, "geosite-"), "geoip-"))
	}
	isDomain := ip == nil
	var addrs []net.IP
	resolved := !isDomain
	if !isDomain {
		addrs = []net.IP{ip}
	}
	var notes []string
	for _, e := range config.Entries(set) {
		if !resolved && e.HasIP() {
			// like the core: the name is resolved before the first step that
			// matches IP addresses, except known foreign sites
			resolved = true
			switch {
			case setMatches(filepath.Join(ruleSetDir, config.ForeignSites+".srs"), host):
				notes = append(notes, i18n.T("已知的境外网站不做解析，按域名交给后面的规则，按 IP 判断的规则（IP 段、IP 归属地）不会命中"))
			case set.ResolveDNS == store.ResolveRemote:
				// the remote DNS goes through the proxy, which the tester does not use
				notes = append(notes, i18n.T("解析方式是「接管 DNS 解析」，试一试不经过代理查询，按 IP 判断的规则（IP 段、IP 归属地）已跳过"))
			default:
				if addrs = resolveDirect(set.DirectDNS, host); len(addrs) > 0 {
					res.Resolved = addrs[0].String()
				} else {
					notes = append(notes, i18n.T("直连 DNS 没有解析出这个域名的 IP，按 IP 判断的规则（IP 段、IP 归属地）已跳过"))
				}
			}
		}
		value, hit := entryMatches(ruleSetDir, e, host, isDomain, addrs, pkg)
		if !hit {
			continue
		}
		res.Target, res.Value = e.Target, value
		switch e.Source {
		case "ads":
			res.Step = stepAds
		case "scene":
			res.Step, res.Scene = "scene", e.Scene
		default:
			res.Step, res.Rule = "rule", e.Index
			if e.Index >= 0 && e.Index < len(set.Rules) {
				res.Kind = set.Rules[e.Index].Kind
			}
		}
		res.Note = strings.Join(append(notes, missingNote(missing)...), "\n")
		return marshal(res)
	}
	res.Step, res.Target = stepFinal, "final"
	if set.RouteMode == "direct" {
		res.Target = store.TargetDirect
	}
	res.Note = strings.Join(append(notes, missingNote(missing)...), "\n")
	return marshal(res)
}

// missingNote explains that rule-sets which are not downloaded yet were not
// checked (an earlier step might have caught the host otherwise).
func missingNote(missing []string) []string {
	if len(missing) == 0 {
		return nil
	}
	return []string{fmt.Sprintf(i18n.T("这些规则集还没下载，暂时没法判断：%s。连接一次后会自动下载。"), strings.Join(missing, i18n.T("、")))}
}

// entryMatches checks one step against a domain name (when isDomain) and
// its addresses, or an IP address (addrs alone); value is what matched.
func entryMatches(dir string, e config.Entry, host string, isDomain bool, addrs []net.IP, pkg string) (value string, hit bool) {
	for _, p := range e.Packages() {
		if pkg != "" && p == pkg {
			return p, true
		}
	}
	if isDomain {
		domain, suffix, keyword, regex := e.Domains()
		for _, d := range domain {
			if host == d {
				return d, true
			}
		}
		for _, d := range suffix {
			if host == d || strings.HasSuffix(host, "."+d) {
				return d, true
			}
		}
		for _, k := range keyword {
			if strings.Contains(host, k) {
				return k, true
			}
		}
		for _, r := range regex {
			if re, err := regexp.Compile(r); err == nil && re.MatchString(host) {
				return r, true
			}
		}
	}
	for _, ip := range addrs {
		for _, c := range e.CIDRs() {
			if _, n, err := net.ParseCIDR(c); err == nil && n.Contains(ip) {
				return c, true
			}
		}
	}
	ruleSetMatcherMu.Lock()
	match := ruleSetMatcher
	ruleSetMatcherMu.Unlock()
	if match == nil {
		return "", false
	}
	label := func(set string) string {
		if config.IsURLRuleSet(set) {
			return i18n.T("规则集网址")
		}
		return strings.TrimPrefix(strings.TrimPrefix(set, "geosite-"), "geoip-")
	}
	for _, set := range e.RuleSetTags() {
		path := filepath.Join(dir, set+".srs")
		// a rule-set from an address may hold domains and IP ranges alike
		if isDomain && !strings.HasPrefix(set, "geoip-") {
			if ok, err := match(path, host); err == nil && ok {
				return label(set), true
			}
		}
		if strings.HasPrefix(set, "geosite-") {
			continue
		}
		for _, ip := range addrs {
			if ok, err := match(path, ip.String()); err == nil && ok {
				return label(set), true
			}
		}
	}
	return "", false
}

// pcOnlyScenes are scenes of NextBox for Windows that do nothing useful on a
// phone (Steam and other game downloads). They stay in the settings, so a
// backup carries them back to Windows unchanged, but the phone neither
// routes by them nor shows them.
var pcOnlyScenes = map[string]bool{"games": true}

// phoneSettings is set as the phone routes by it: the PC-only scenes off.
func phoneSettings(set store.Settings) store.Settings {
	scenes := make([]store.Scene, len(set.Scenes))
	for i, sc := range set.Scenes {
		if pcOnlyScenes[sc.ID] {
			sc.Enabled = false
		}
		scenes[i] = sc
	}
	set.Scenes = scenes
	return set
}

// setMatches reports whether the rule-set file at path matches host.
func setMatches(path, host string) bool {
	ruleSetMatcherMu.Lock()
	match := ruleSetMatcher
	ruleSetMatcherMu.Unlock()
	if match == nil {
		return false
	}
	ok, err := match(path, host)
	return err == nil && ok
}
