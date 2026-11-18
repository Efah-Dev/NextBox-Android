package nbcore

// The routing of NextBox for Android before 2.0: a fixed path instead of an
// ordered list,
//
//	ads → LAN → block rules → direct rules → policy group rules → proxy rules
//	→ bypass mainland China → everything else
//
// where a more specific domain was checked first. 2.0 checks the rules in
// their stored order, like NextBox for Windows; MigrateRoute puts the rules
// of an older version in the order the old path checked them, once, so that
// the traffic goes where it went before. Only that code is kept here.

import (
	"sort"
	"strings"

	"nextbox/internal/config"
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

// MigrateRoute puts the stored rules in the order the routing of Android
// 1.x checked them (see above), with ad blocking first and the scenes last,
// and saves them. The rules that path left out (switched off, or program
// rules of the desktop app) keep their relative order before the scenes.
// directOnly is the 1.x choice "everything direct". It is run once, by the
// app, when 2.0 starts for the first time on data of an older version.
func MigrateRoute(directOnly bool) error {
	s, err := current()
	if err != nil {
		return err
	}
	return s.SaveSettings(migrateRules(s.Settings(), directOnly))
}

// migrateRules is MigrateRoute on set: the rules in the old path order and
// the routing mode from the 1.x choice.
func migrateRules(set store.Settings, directOnly bool) store.Settings {
	rules := []store.Rule{{Kind: store.KindAds, Values: []string{}}}
	taken := map[int]bool{}
	for _, r := range pathOrder(set.Rules) {
		rules = append(rules, r.Rule)
		taken[r.Orig] = true
	}
	for i, r := range set.Rules {
		if !taken[i] && !r.IsStep() {
			rules = append(rules, r)
		}
	}
	for _, d := range config.SceneDefs {
		rules = append(rules, store.Rule{Kind: store.KindScene, Values: []string{d.ID}})
	}
	set.Rules = rules
	if directOnly {
		set.RouteMode = "direct"
	} else {
		set.RouteMode = "rule"
	}
	return set
}
