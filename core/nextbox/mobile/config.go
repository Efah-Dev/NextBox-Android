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
	set := s.Settings()
	var node *store.Profile
	if id := s.SelectedID(); id != 0 {
		if p, ok := s.Profile(id); ok {
			node = &p
		}
	}
	ensureRuleSets(ruleSetDir, set, "")
	cfg, err := config.Build(config.Input{
		Settings:            set,
		Node:                node,
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
	// after sniff and hijack-dns, before every other rule
	at := 0
	for at < len(rules) {
		m, _ := rules[at].(map[string]any)
		if m["action"] != "sniff" && m["action"] != "hijack-dns" {
			break
		}
		at++
	}
	out := append([]any{}, rules[:at]...)
	out = append(out, rule)
	route["rules"] = append(out, rules[at:]...)
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
	cfg, err := config.BuildTest(s.Settings(), s.ProfilesByIDs(parseIDs(ids)), "", secret, int(port))
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
			for _, r := range rules {
				if m, ok := r.(map[string]any); ok {
					if v, ok := m["process_name"]; ok {
						m["package_name"] = v
						delete(m, "process_name")
						usesPackages = true
					}
				}
			}
		}
		route["find_process"] = s.TunEnabled && (opt.FindProcess || usesPackages)
	}
	if exp, ok := cfg["experimental"].(map[string]any); ok {
		delete(exp, "clash_api")
		if cache, ok := exp["cache_file"].(map[string]any); ok && opt.FakeIP {
			cache["store_fakeip"] = true
		}
	}
	if opt.FakeIP && s.TunEnabled && s.RouteMode != "direct" {
		addFakeIP(cfg, s.EnableIPv6)
	}
}

// addFakeIP makes A/AAAA queries that would go to the remote DNS server
// return fake addresses instead. Rules sending a domain to the direct DNS
// (bypass rules, mainland domains) come first and still get real answers.
func addFakeIP(cfg map[string]any, ipv6 bool) {
	dns, ok := cfg["dns"].(map[string]any)
	if !ok || dns["final"] != config.TagDNSRemote {
		return
	}
	server := map[string]any{"type": "fakeip", "tag": "dns-fakeip", "inet4_range": "198.18.0.0/15"}
	types := []string{"A"}
	if ipv6 {
		server["inet6_range"] = "fc00::/18"
		types = append(types, "AAAA")
	}
	servers, _ := dns["servers"].([]any)
	dns["servers"] = append(servers, server)
	rules, _ := dns["rules"].([]any)
	dns["rules"] = append(rules, map[string]any{"query_type": types, "server": "dns-fakeip"})
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
