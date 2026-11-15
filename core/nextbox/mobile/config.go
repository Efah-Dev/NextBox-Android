package nbcore

import (
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/netip"
	"net/url"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"

	"nextbox/internal/config"
	"nextbox/internal/i18n"
	"nextbox/internal/ruleset"
	"nextbox/internal/store"

	"golang.org/x/net/idna"
)

var ruleSetMu sync.Mutex

// BuildConfig generates the sing-box configuration for the selected node and
// the current settings, adapted to the Android client: the TUN inbound is
// opened by VpnService, process rules match package names, and the clash API
// is left out (status comes from the libbox command server).
//
// Rule-sets that are not on disk yet are installed (bundled ones) or
// downloaded; when that fails the affected rules are skipped, so connecting
// never fails because of a missing rule-set.
//
// options is JSON with Android-only switches (see androidOptions).
func BuildConfig(ruleSetDir, options string) (string, error) {
	s, err := current()
	if err != nil {
		return "", err
	}
	opt := androidOptions{FakeIP: true, LogLevel: "warn"}
	if options != "" {
		if err := json.Unmarshal([]byte(options), &opt); err != nil {
			return "", err
		}
	}
	// the rules in the order of the path the rules page shows
	set, _ := pathSettings(s.Settings())
	var node *store.Profile
	if id := s.SelectedID(); id != 0 {
		if p, ok := s.Profile(id); ok {
			node = &p
		}
	}
	ensureRuleSets(ruleSetDir, set, "")
	// The FakeIP of the shared builder answers every proxied domain with a
	// fake address (IPv6 too unless turned off below).
	set.TunFakeIP = opt.FakeIP
	cfg, err := config.Build(config.Input{
		Settings:            set,
		Node:                node,
		Member:              s.Profile,
		Policies:            policyInputs(s, set),
		RuleSetDir:          ruleSetDir,
		SkipMissingRuleSets: true,
	})
	if err != nil {
		return "", err
	}
	adaptForAndroid(cfg, set, opt)
	if set.TunEnabled {
		directNodeServers(cfg, s.Profiles(0))
	}
	return marshal(cfg)
}

// directNodeServers routes every node server direct. NextBox stays inside its
// own VPN (the TUN stack needs it), so without this rule latency tests of
// other nodes would be relayed through the current node.
func directNodeServers(cfg map[string]any, profiles []store.Profile) {
	route, ok := cfg["route"].(map[string]any)
	if !ok {
		return
	}
	var domains, cidrs []string
	seen := map[string]bool{}
	for _, p := range profiles {
		host, _ := p.Outbound["server"].(string)
		if host == "" || seen[host] {
			continue
		}
		seen[host] = true
		if ip, err := netip.ParseAddr(host); err == nil {
			cidrs = append(cidrs, netip.PrefixFrom(ip, ip.BitLen()).String())
		} else {
			domains = append(domains, host)
			// connections carry the ASCII form of an international domain name
			if ascii, err := idna.Lookup.ToASCII(host); err == nil && ascii != host {
				domains = append(domains, ascii)
			}
		}
	}
	if len(domains)+len(cidrs) == 0 {
		return
	}
	rule := map[string]any{"outbound": config.TagDirect}
	if len(domains) > 0 {
		rule["domain"] = domains
	}
	if len(cidrs) > 0 {
		rule["ip_cidr"] = cidrs
	}
	rules, _ := route["rules"].([]any)
	// First, before sniffing: a sniffed TLS name (Reality, CDN fronting) often
	// differs from the server address, and once sniffed the rules match that
	// name instead of the destination. Node traffic is never DNS, so going
	// ahead of hijack-dns is harmless.
	route["rules"] = append([]any{rule}, rules...)
}

// androidOptions are the Android-only knobs of BuildConfig.
type androidOptions struct {
	// FakeIP answers A/AAAA queries for proxied domains with addresses from
	// 198.18.0.0/15 at once, so apps connect without waiting for a DNS round
	// trip through the proxy; the proxy server resolves the real address.
	FakeIP bool `json:"fakeIP"`
	// FindProcess looks up the app owning every connection (shown on the
	// activity page). Costs a system call per connection, so it is off unless
	// asked for or needed by package rules.
	FindProcess bool `json:"findProcess"`
	// LogLevel of the core. Logging every connection at "info" costs CPU on
	// a phone; "warn" keeps only problems.
	LogLevel string `json:"logLevel"`
	// LogFile, when set, also writes the core log to this file (debug builds).
	LogFile string `json:"logFile"`
}

// BuildTestConfig generates the latency test config for the given nodes (a
// comma separated id list): one outbound per node behind a local mixed
// inbound on port, each reached with username "n<id>" and password secret.
func BuildTestConfig(ids string, port int32, secret string) (string, error) {
	s, err := current()
	if err != nil {
		return "", err
	}
	cfg, err := config.BuildTest(s.Settings(), s.ProfilesByIDs(parseIDs(ids)), s.Profile, "", secret, int(port))
	if err != nil {
		return "", err
	}
	// No clash API in the Android build, and no interface monitor without
	// the platform interface. While the VPN runs, this instance's traffic
	// goes through it and BuildConfig routes every node server direct.
	delete(cfg, "experimental")
	if route, ok := cfg["route"].(map[string]any); ok {
		delete(route, "auto_detect_interface")
	}
	return marshal(cfg)
}

// SelectedNodeName returns the display name of the selected node ("" if none).
func SelectedNodeName() string {
	s, err := current()
	if err != nil {
		return ""
	}
	if p, ok := s.Profile(s.SelectedID()); ok {
		return p.Name
	}
	return ""
}

func adaptForAndroid(cfg map[string]any, s store.Settings, opt androidOptions) {
	level := opt.LogLevel
	if level == "" {
		level = "warn"
	}
	logCfg := map[string]any{"level": level}
	if opt.LogFile != "" {
		logCfg["output"] = opt.LogFile
		logCfg["timestamp"] = true
	}
	cfg["log"] = logCfg

	if inbounds, ok := cfg["inbounds"].([]any); ok {
		kept := inbounds[:0]
		for _, in := range inbounds {
			m, ok := in.(map[string]any)
			if ok && m["type"] == "mixed" && s.TunEnabled && !s.AllowLAN {
				// In VPN mode nothing needs the local proxy port, and an open,
				// unauthenticated port lets any app on the phone use or detect it.
				continue
			}
			kept = append(kept, in)
			if !ok || m["type"] != "tun" {
				continue
			}
			delete(m, "interface_name")
			delete(m, "strict_route")
			// The "system" TCP stack (also used by "mixed") never completes a
			// handshake on recent Android builds; the user space gVisor stack
			// works everywhere.
			m["stack"] = "gvisor"
		}
		cfg["inbounds"] = kept
	}
	usesPackages := false
	if route, ok := cfg["route"].(map[string]any); ok {
		if rules, ok := route["rules"].([]any); ok {
			kept := rules[:0]
			for _, r := range rules {
				m, ok := r.(map[string]any)
				if !ok {
					kept = append(kept, r)
					continue
				}
				// Program rules from the desktop app ("chrome.exe") mean
				// nothing here; a rule left without a condition goes too.
				if _, ok := m["process_name"]; ok {
					delete(m, "process_name")
					if !hasCondition(m) {
						continue
					}
				}
				if _, ok := m["package_name"]; ok {
					usesPackages = true
				}
				kept = append(kept, m)
			}
			route["rules"] = kept
		}
		route["find_process"] = s.TunEnabled && (opt.FindProcess || usesPackages)
	}
	if exp, ok := cfg["experimental"].(map[string]any); ok {
		delete(exp, "clash_api")
	}
	if !s.EnableIPv6 {
		ipv4OnlyFakeIP(cfg)
	}
}

// hasCondition reports whether a route rule still matches on something.
func hasCondition(rule map[string]any) bool {
	for k := range rule {
		switch k {
		case "outbound", "action", "invert":
		default:
			return true
		}
	}
	return false
}

// ipv4OnlyFakeIP keeps FakeIP to A queries when IPv6 is off: the phone then
// asks for AAAA records too, and a fake IPv6 answer would lead apps to try a
// route the VPN does not carry.
func ipv4OnlyFakeIP(cfg map[string]any) {
	dns, ok := cfg["dns"].(map[string]any)
	if !ok {
		return
	}
	servers, _ := dns["servers"].([]any)
	for _, sv := range servers {
		if m, ok := sv.(map[string]any); ok && m["type"] == "fakeip" {
			delete(m, "inet6_range")
		}
	}
	rules, _ := dns["rules"].([]any)
	for _, r := range rules {
		if m, ok := r.(map[string]any); ok && m["server"] == config.TagFakeIP {
			m["query_type"] = []string{"A"}
		}
	}
}

// UpdateRuleSets downloads every rule-set the current settings need again.
// proxy is an optional "http://127.0.0.1:port".
func UpdateRuleSets(ruleSetDir, proxy string) (string, error) {
	s, err := current()
	if err != nil {
		return "", err
	}
	names := config.RequiredRuleSets(s.Settings())
	if len(names) == 0 {
		return i18n.T("当前设置不需要规则集"), nil
	}
	ruleSetMu.Lock()
	defer ruleSetMu.Unlock()
	if err := fetchRuleSets(ruleSetDir, s.Settings().RuleSetMirror, names, proxy); err != nil {
		return "", err
	}
	return fmt.Sprintf(i18n.T("已更新 %d 个规则集"), len(names)), nil
}

func ensureRuleSets(dir string, s store.Settings, proxy string) {
	ruleSetMu.Lock()
	defer ruleSetMu.Unlock()
	missing := config.MissingRuleSets(dir, config.RequiredRuleSets(s))
	if len(missing) > 0 {
		fetchRuleSets(dir, s.RuleSetMirror, missing, proxy)
	}
}

// fetchRuleSets makes the named .srs files available: bundled copies for the
// local mirror, downloads for everything else (falling back to a bundled copy
// when the download fails and no file exists yet).
func fetchRuleSets(dir, mirror string, names []string, proxy string) error {
	if err := os.MkdirAll(dir, 0o755); err != nil {
		return err
	}
	transport := http.DefaultTransport.(*http.Transport).Clone()
	if proxy != "" {
		if pu, err := url.Parse(proxy); err == nil {
			transport.Proxy = http.ProxyURL(pu)
		}
	}
	client := &http.Client{Transport: transport, Timeout: 20 * time.Second}
	var errs []error
	for _, name := range names {
		dest := filepath.Join(dir, name+".srs")
		if mirror == store.LocalRuleSetMirror && ruleset.Has(name) {
			if err := ruleset.Install(name, dest); err != nil {
				errs = append(errs, err)
			}
			continue
		}
		m := mirror
		if m == store.LocalRuleSetMirror || m == "" {
			m = store.RemoteRuleSetMirror
		}
		if err := download(client, config.RuleSetURL(m, name), dest); err != nil {
			if _, statErr := os.Stat(dest); statErr != nil && ruleset.Has(name) {
				ruleset.Install(name, dest)
			}
			errs = append(errs, fmt.Errorf("%s: %w", name, err))
		}
	}
	return errors.Join(errs...)
}

func download(client *http.Client, rawURL, dest string) error {
	resp, err := client.Get(rawURL)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("HTTP %d", resp.StatusCode)
	}
	b, err := io.ReadAll(io.LimitReader(resp.Body, 64<<20))
	if err != nil {
		return err
	}
	if len(b) == 0 || strings.HasPrefix(strings.TrimSpace(string(b[:min(len(b), 16)])), "<") {
		return errors.New(i18n.T("下载的规则集无效"))
	}
	tmp := dest + ".tmp"
	if err := os.WriteFile(tmp, b, 0o644); err != nil {
		return err
	}
	return os.Rename(tmp, dest)
}
