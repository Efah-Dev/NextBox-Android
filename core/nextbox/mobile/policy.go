package nbcore

import (
	_ "embed"
	"encoding/json"
	"errors"
	"strings"

	"nextbox/internal/config"
	"nextbox/internal/geo"
	"nextbox/internal/i18n"
	"nextbox/internal/store"
)

// The words that point to a region in node names, the same list the desktop
// app uses: [code, Chinese words, Latin words].
//
//go:embed places.json
var placesJSON []byte

func init() { geo.LoadPlaces(placesJSON) }

// Places lists the regions a policy group can pick: [{"code","name"}], name
// being the first Chinese word of each region.
func Places() string {
	var raw [][]any
	json.Unmarshal(placesJSON, &raw)
	type place struct {
		Code string `json:"code"`
		Name string `json:"name"`
	}
	out := []place{}
	for _, p := range raw {
		if len(p) < 2 {
			continue
		}
		code, _ := p[0].(string)
		name := code
		switch zh := p[1].(type) {
		case []any:
			if len(zh) > 0 {
				name, _ = zh[0].(string)
			}
		case string:
			name, _, _ = strings.Cut(zh, " ")
		}
		out = append(out, place{code, name})
	}
	b, _ := json.Marshal(out)
	return string(b)
}

// policyMembers lists the nodes a policy group uses, as many as the core
// loads; chain proxies that cannot work are left out.
func policyMembers(s *store.Store, p store.Policy) []store.Profile {
	var out []store.Profile
	add := func(n store.Profile) bool {
		if chainError(s, n) != nil {
			return true
		}
		out = append(out, n)
		return len(out) < store.MaxPolicyMembers
	}
	if p.Match == store.PolicyManual {
		for _, n := range s.ProfilesByIDs(p.Members) {
			if !add(n) {
				break
			}
		}
		return out
	}
	keywords := p.Keywords()
	for _, n := range s.Profiles(p.GroupID) {
		match := false
		switch p.Match {
		case store.PolicyCountry:
			match = p.Country != "" && regionOf(n) == p.Country
		case store.PolicyKeyword:
			name := strings.ToLower(n.Name)
			for _, k := range keywords {
				if strings.Contains(name, k) {
					match = true
					break
				}
			}
		}
		if match && !add(n) {
			break
		}
	}
	return out
}

// policyInputs are the policy groups the rules use, with their members.
// Groups that are gone or empty are left out: their rules use the node.
func policyInputs(s *store.Store, set store.Settings) []config.PolicyInput {
	var out []config.PolicyInput
	for _, id := range config.UsedPolicies(set) {
		p, ok := s.Policy(id)
		if !ok {
			continue
		}
		if members := policyMembers(s, p); len(members) > 0 {
			out = append(out, config.PolicyInput{Policy: p, Members: members})
		}
	}
	return out
}

type policyMember struct {
	ID      int64  `json:"id"`
	Name    string `json:"name"`
	Type    string `json:"type"`
	Latency int    `json:"latency"`
	Region  string `json:"region"`
}

type policyView struct {
	store.Policy
	Nodes []policyMember `json:"nodes"`
	Rules int            `json:"rules"` // how many rules and scenes use it
}

// Policies lists the policy groups with the nodes each one matches now.
func Policies() (string, error) {
	s, err := current()
	if err != nil {
		return "", err
	}
	set := s.Settings()
	uses := map[int64]int{}
	for _, r := range set.Rules {
		uses[store.TargetPolicy(r.Target)]++
	}
	for _, sc := range set.Scenes {
		uses[store.TargetPolicy(sc.Target)]++
	}
	out := []policyView{}
	for _, p := range s.Policies() {
		v := policyView{Policy: p, Nodes: []policyMember{}, Rules: uses[p.ID]}
		for _, n := range policyMembers(s, p) {
			v.Nodes = append(v.Nodes, policyMember{ID: n.ID, Name: n.Name, Type: n.Type, Latency: n.Latency, Region: regionOf(n)})
		}
		out = append(out, v)
	}
	return marshal(out)
}

// PreviewPolicy returns the nodes a policy group (JSON, not saved) matches.
func PreviewPolicy(js string) (string, error) {
	s, err := current()
	if err != nil {
		return "", err
	}
	var p store.Policy
	if err := json.Unmarshal([]byte(js), &p); err != nil {
		return "", err
	}
	out := []policyMember{}
	for _, n := range policyMembers(s, p) {
		out = append(out, policyMember{ID: n.ID, Name: n.Name, Type: n.Type, Latency: n.Latency, Region: regionOf(n)})
	}
	return marshal(out)
}

// SavePolicy creates (id 0) or updates a policy group and returns it.
func SavePolicy(js string) (string, error) {
	s, err := current()
	if err != nil {
		return "", err
	}
	var p store.Policy
	if err := json.Unmarshal([]byte(js), &p); err != nil {
		return "", err
	}
	p.Name = strings.TrimSpace(p.Name)
	if p.Name == "" {
		return "", errors.New(i18n.T("请填写名称"))
	}
	switch p.Match {
	case store.PolicyCountry:
		if p.Country == "" {
			return "", errors.New(i18n.T("请选择地区"))
		}
	case store.PolicyKeyword:
		if len(p.Keywords()) == 0 {
			return "", errors.New(i18n.T("请填写关键词"))
		}
	case store.PolicyManual:
		if len(p.Members) == 0 {
			return "", errors.New(i18n.T("请至少选择一个节点"))
		}
	default:
		return "", errors.New(i18n.T("未知的筛选方式"))
	}
	if p.Mode != store.PolicySelect {
		p.Mode = store.PolicyAuto
	}
	saved, err := s.SavePolicy(p)
	if err != nil {
		return "", err
	}
	return marshal(saved)
}

// DeletePolicy removes a policy group; its rules go to the selected node.
func DeletePolicy(id int64) error {
	s, err := current()
	if err != nil {
		return err
	}
	if err := s.DeletePolicy(id); err != nil {
		return err
	}
	set := s.Settings()
	target := store.PolicyTarget(id)
	changed := false
	for i := range set.Rules {
		if set.Rules[i].Target == target {
			set.Rules[i].Target, changed = store.TargetProxy, true
		}
	}
	for i := range set.Scenes {
		if set.Scenes[i].Target == target {
			set.Scenes[i].Target, changed = store.TargetProxy, true
		}
	}
	if changed {
		return s.SaveSettings(set)
	}
	return nil
}

// PolicyUsed reports whether the rules send traffic to the policy group, or
// the connection uses it (SetSelectedPolicy).
func PolicyUsed(id int64) bool {
	s, err := current()
	if err != nil {
		return false
	}
	if id != 0 && s.SelectedPolicy() == id {
		return true
	}
	for _, used := range config.UsedPolicies(s.Settings()) {
		if used == id {
			return true
		}
	}
	return false
}

// SelectPolicyNode picks the node of a policy group in "select" mode.
func SelectPolicyNode(id, node int64) error {
	s, err := current()
	if err != nil {
		return err
	}
	return s.SetPolicySelected(id, node)
}
