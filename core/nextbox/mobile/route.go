package nbcore

// Routing as NextBox for Android shows it: a fixed path instead of an ordered
// rule list. Traffic is checked step by step and leaves at the first step
// that matches:
//
//	ads → LAN → block rules → direct rules → policy group rules → proxy rules
//	→ bypass mainland China → everything else (the node, or direct)
//
// The rules themselves are stored in the shared model (store.Rule, the same
// as NextBox for Windows, so backups move between the two); only the order
// they are checked in is decided here. Within the rules, a more specific
// domain wins: with "example.com → direct" and "api.example.com → proxy",
// api.example.com goes through the proxy although direct rules come first.
//
// The stored order is the one NextBox for Windows checks the rules in, with
// places for ad blocking and the scenes (store.KindAds, store.KindScene).
// Android keeps it as it is and puts rules added here where the path checks
// them (settleRules), so a backup that goes through the phone comes back to
// the computer unchanged.

import (
	"encoding/json"
	"errors"
	"fmt"
	"net"
	"path/filepath"
	"regexp"
	"slices"
	"sort"
	"strings"
	"sync"

	"nextbox/internal/config"
	"nextbox/internal/i18n"
	"nextbox/internal/store"
)

// Steps of the path, as the app names them.
const (
	stepAds    = "ads"
	stepLAN    = "lan"
	stepBlock  = "block"
	stepDirect = "direct"
	stepPolicy = "policy"
	stepProxy  = "proxy"
	stepCN     = "cn"
	stepFinal  = "final"
)

var ruleSteps = []string{stepBlock, stepDirect, stepPolicy, stepProxy}

// ruleStep is the step a rule target belongs to (an index into ruleSteps).
func ruleStep(target string) int {
	switch {
	case target == store.TargetBlock:
		return 0
	case target == store.TargetDirect:
		return 1
	case store.TargetPolicy(target) != 0:
		return 2
	default:
		return 3
	}
}

// pathRule is one rule in the order the core checks it.
type pathRule struct {
	store.Rule
	Step string // one of ruleSteps
	Orig int    // index in Settings.Rules
}

type domainItem struct {
	kind, value, target string
	step, home          int // step of the rule / step it is checked in
	orig                int
}

func (d domainItem) labels() int { return strings.Count(d.value, ".") + 1 }

// moreSpecific reports whether domain item v names part of what suffix u
// matches (and so must be checked before u).
func moreSpecific(v domainItem, u domainItem) bool {
	if u.kind != "suffix" {
		return false
	}
	if strings.HasSuffix(v.value, "."+u.value) {
		return true
	}
	return v.kind == "domain" && v.value == u.value
}

// pathOrder flattens the rules into the order of the path. Domain rules
// ("domain", "suffix") are checked by specificity: a domain that a rule of an
// earlier step covers with a broader suffix moves up to that step, and within
// a step longer domains come first. Other kinds keep their step and order.
func pathOrder(rules []store.Rule) []pathRule {
	var items []domainItem
	for i, r := range rules {
		if r.Disabled || r.IsStep() || (r.Kind != "domain" && r.Kind != "suffix") {
			continue
		}
		st := ruleStep(r.Target)
		for _, v := range r.Values {
			items = append(items, domainItem{kind: r.Kind, value: v, target: r.Target, step: st, home: st, orig: i})
		}
	}
	for i := range items {
		for _, u := range items {
			if u.step < items[i].home && moreSpecific(items[i], u) {
				items[i].home = u.step
			}
		}
	}
	sort.SliceStable(items, func(a, b int) bool {
		x, y := items[a], items[b]
		if x.home != y.home {
			return x.home < y.home
		}
		if x.labels() != y.labels() {
			return x.labels() > y.labels()
		}
		if x.kind != y.kind {
			return x.kind == "domain"
		}
		return x.step < y.step
	})

	var out []pathRule
	next := 0
	for st, name := range ruleSteps {
		for ; next < len(items) && items[next].home == st; next++ {
			it := items[next]
			// consecutive values with the same kind, target and origin share one rule
			if n := len(out); n > 0 && out[n-1].Step == name && out[n-1].Kind == it.kind &&
				out[n-1].Target == it.target && out[n-1].Orig == it.orig {
				out[n-1].Values = append(out[n-1].Values, it.value)
				continue
			}
			out = append(out, pathRule{Rule: store.Rule{Kind: it.kind, Values: []string{it.value}, Target: it.target}, Step: name, Orig: it.orig})
		}
		for i, r := range rules {
			// program rules ("chrome.exe") are the desktop app's; they stay in the data
			if r.Disabled || r.IsStep() || r.Kind == "process" || r.Kind == "domain" || r.Kind == "suffix" || ruleStep(r.Target) != st {
				continue
			}
			out = append(out, pathRule{Rule: r, Step: name, Orig: i})
		}
	}
	return out
}

// pathSettings returns settings whose rules are in path order, ready for the
// shared config builder: ad blocking first, then the rules, then the scenes
// (on Android only bypassing mainland China is ever on). index has one entry
// per rule of the result; Orig is -1 for the places of ads and the scenes.
func pathSettings(set store.Settings) (store.Settings, []pathRule) {
	ads := store.Rule{Kind: store.KindAds, Values: []string{}}
	rules := []store.Rule{ads}
	index := []pathRule{{Rule: ads, Step: stepAds, Orig: -1}}
	for _, r := range pathOrder(set.Rules) {
		rules = append(rules, r.Rule)
		index = append(index, r)
	}
	for _, d := range config.SceneDefs {
		sc := store.Rule{Kind: store.KindScene, Values: []string{d.ID}}
		rules = append(rules, sc)
		index = append(index, pathRule{Rule: sc, Step: stepCN, Orig: -1})
	}
	set.Rules = rules
	return set, index
}

// rank is where a stored rule sits on the path: ads 0, the rule steps 1-4,
// bypassing mainland China 5; -1 for the other scenes, which Android never
// switches on.
func rank(r store.Rule) int {
	switch r.Kind {
	case store.KindAds:
		return 0
	case store.KindScene:
		if len(r.Values) > 0 && r.Values[0] == "cn" {
			return len(ruleSteps) + 1
		}
		return -1
	}
	return 1 + ruleStep(r.Target)
}

// placeRule inserts r into the stored rules where the path checks it: after
// the last rule of its step, else after the last thing checked before that
// step, else before the first thing checked after it.
func placeRule(rules []store.Rule, r store.Rule) []store.Rule {
	k := rank(r)
	at := -1
	for i, o := range rules {
		if rank(o) == k {
			at = i + 1
		}
	}
	if at < 0 {
		for i, o := range rules {
			if q := rank(o); q >= 0 && q < k {
				at = i + 1
			}
		}
	}
	if at < 0 {
		at = len(rules)
		for i, o := range rules {
			if rank(o) > k {
				at = i
				break
			}
		}
	}
	return slices.Insert(rules, at, r)
}

// settleRules keeps the stored order when the rules page saves next: rules
// that were there keep their place, a changed one (same kind and target,
// other values) takes the place of the one it replaces, and new ones go
// where the path checks them (placeRule).
func settleRules(old, next []store.Rule) []store.Rule {
	key := func(r store.Rule) string { b, _ := json.Marshal(r); return string(b) }
	free := map[string][]int{}
	for i, r := range old {
		free[key(r)] = append(free[key(r)], i)
	}
	used := make([]bool, len(old))
	pos := make([]int, len(next))
	for i, r := range next {
		pos[i] = -1
		if l := free[key(r)]; len(l) > 0 {
			pos[i], used[l[0]], free[key(r)] = l[0], true, l[1:]
		}
	}
	for i, r := range next {
		if pos[i] >= 0 || r.IsStep() {
			continue
		}
		for j, o := range old {
			if !used[j] && !o.IsStep() && o.Kind == r.Kind && o.Target == r.Target && o.Disabled == r.Disabled {
				pos[i], used[j] = j, true
				break
			}
		}
	}
	idx := make([]int, 0, len(next))
	for i := range next {
		if pos[i] >= 0 {
			idx = append(idx, i)
		}
	}
	sort.SliceStable(idx, func(a, b int) bool { return pos[idx[a]] < pos[idx[b]] })
	out := make([]store.Rule, 0, len(next))
	for _, i := range idx {
		out = append(out, next[i])
	}
	for i, r := range next {
		if pos[i] < 0 {
			out = placeRule(out, r)
		}
	}
	return out
}

// scenesToRules turns the desktop app's ready-made scenes (other than
// bypassing mainland China, which is the home page switch) into ordinary
// rules, which the Android rules page shows and can edit. The rules take the
// scene's place, so the computer checks them as before. It reports whether
// anything changed.
func scenesToRules(set *store.Settings) bool {
	changed := false
	for i, sc := range set.Scenes {
		if sc.ID == "cn" || !sc.Enabled {
			continue
		}
		def, ok := sceneDefFor(sc.ID)
		if ok {
			var values []string
			for _, s := range def.Sets {
				if v, ok := strings.CutPrefix(s, "geosite-"); ok {
					values = append(values, v)
				}
			}
			target := sc.Target
			if target == "" {
				target = store.TargetProxy
			}
			if len(values) > 0 {
				r := store.Rule{Kind: "geosite", Values: values, Target: target}
				at := slices.IndexFunc(set.Rules, func(m store.Rule) bool {
					return m.Kind == store.KindScene && len(m.Values) > 0 && m.Values[0] == sc.ID
				})
				if at >= 0 {
					set.Rules = slices.Insert(set.Rules, at, r)
				} else {
					set.Rules = placeRule(set.Rules, r)
				}
			}
		}
		set.Scenes[i].Enabled = false
		changed = true
	}
	return changed
}

func sceneDefFor(id string) (config.SceneDef, bool) {
	for _, d := range config.SceneDefs {
		if d.ID == id {
			return d, true
		}
	}
	return config.SceneDef{}, false
}

// SetRoute applies the routing choice of the home page: "direct" sends
// everything direct, anything else keeps the core in rule mode (the rules
// apply, unmatched traffic goes to the node); bypass switches the "mainland
// China" step.
func SetRoute(scope string, bypass bool) error {
	s, err := current()
	if err != nil {
		return err
	}
	set := s.Settings()
	if scope == "direct" {
		set.RouteMode = "direct"
	} else {
		set.RouteMode = "rule"
	}
	for i := range set.Scenes {
		if set.Scenes[i].ID == "cn" {
			set.Scenes[i].Enabled = bypass
		}
	}
	return s.SaveSettings(set)
}

// SaveRules replaces the rules (JSON list of store.Rule as State gives them,
// the places of ads and the scenes included).
func SaveRules(js string) error {
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
	set.Rules = settleRules(set.Rules, rules)
	return s.SaveSettings(set)
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
	Input  string `json:"input"`
	Step   string `json:"step"`   // one of the step constants
	Target string `json:"target"` // store.Target*, a policy target, or "final"
	Rule   int    `json:"rule"`   // index in Settings.Rules for a rule step, else -1
	Kind   string `json:"kind,omitempty"`
	Value  string `json:"value,omitempty"` // what matched (a domain, rule-set, package ...)
	Note   string `json:"note,omitempty"`
}

var hostOnly = regexp.MustCompile(`^[a-z0-9.\-_:\[\]]+$`)

// TestRoute tells which step of the path a website or IP (and optionally the
// app making the connection, pkg) leaves at.
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
	set, order := pathSettings(s.Settings())
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
	skippedIP := false
	for _, e := range config.Entries(set) {
		value, hit, skipped := entryMatches(ruleSetDir, e, host, ip, pkg)
		skippedIP = skippedIP || (skipped && e.Source == "rule") // the bypass step's IP part is not the user's doing
		if !hit {
			continue
		}
		res.Target, res.Value = e.Target, value
		switch e.Source {
		case "ads":
			res.Step = stepAds
		case "scene":
			res.Step = stepCN
		default:
			r := order[e.Index]
			// shown at the step of its own target, even when a more specific
			// domain was moved up to be checked before a broader one
			res.Step, res.Rule, res.Kind = ruleSteps[ruleStep(r.Target)], r.Orig, r.Kind
			if res.Step != r.Step {
				res.Note = i18n.T("这个域名比前面规则里更宽的域名更具体，所以先匹配它。")
			}
		}
		res.Note = strings.Join(append(nonEmpty(res.Note), missingNote(missing)...), "\n")
		return marshal(res)
	}
	res.Step, res.Target = stepFinal, "final"
	if set.RouteMode == "direct" {
		res.Target = store.TargetDirect
	}
	var notes []string
	if skippedIP {
		notes = append(notes, i18n.T("按 IP 判断的规则（IP 段、IP 归属地）只对 IP 地址生效，测试域名时已跳过"))
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

func nonEmpty(s string) []string {
	if s == "" {
		return nil
	}
	return []string{s}
}

// entryMatches checks one step against a host; value is what matched.
func entryMatches(dir string, e config.Entry, host string, ip net.IP, pkg string) (value string, hit, skipped bool) {
	for _, p := range e.Packages() {
		if pkg != "" && p == pkg {
			return p, true, false
		}
	}
	if ip == nil {
		domain, suffix, keyword, regex := e.Domains()
		for _, d := range domain {
			if host == d {
				return d, true, false
			}
		}
		for _, d := range suffix {
			if host == d || strings.HasSuffix(host, "."+d) {
				return d, true, false
			}
		}
		for _, k := range keyword {
			if strings.Contains(host, k) {
				return k, true, false
			}
		}
		for _, r := range regex {
			if re, err := regexp.Compile(r); err == nil && re.MatchString(host) {
				return r, true, false
			}
		}
	} else {
		for _, c := range e.CIDRs() {
			if _, n, err := net.ParseCIDR(c); err == nil && n.Contains(ip) {
				return c, true, false
			}
		}
	}
	ruleSetMatcherMu.Lock()
	match := ruleSetMatcher
	ruleSetMatcherMu.Unlock()
	for _, set := range e.RuleSetTags() {
		isIP := strings.HasPrefix(set, "geoip-")
		if isIP != (ip != nil) {
			skipped = skipped || isIP
			continue
		}
		if match == nil {
			continue
		}
		if ok, err := match(filepath.Join(dir, set+".srs"), host); err == nil && ok {
			return strings.TrimPrefix(strings.TrimPrefix(set, "geosite-"), "geoip-"), true, false
		}
	}
	return "", false, skipped
}
