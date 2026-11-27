package nbcore

// Connecting through a policy group (分组代理) instead of one node, as in
// NextBox for Windows: the main selector of the config ("proxy") points at
// the group. The app follows which member the group uses through the
// command server's group status (BoxRunner), with the tags PolicyRuntime
// gives.

import (
	"fmt"
	"slices"

	"nextbox/internal/config"
	"nextbox/internal/i18n"
	"nextbox/internal/store"
)

// SetSelectedPolicy makes the connection use a policy group; 0 goes back to
// the selected node. The caller reloads a running core.
func SetSelectedPolicy(id int64) error {
	s, err := current()
	if err != nil {
		return err
	}
	return s.SetSelectedPolicy(id)
}

// connectInput is the part of config.Input that says what the connection
// uses: the node, and the policy groups with the one used instead of the
// node (Main). BuildConfig and PolicyRuntime both use it, so that the tags
// PolicyRuntime reports are the ones in the config.
func connectInput(s *store.Store, set store.Settings) (config.Input, error) {
	in := config.Input{Settings: set, Member: s.Profile, Policies: policyInputs(s, set), GroupDNS: s.GroupDNS()}
	if p, ok := s.Profile(s.SelectedID()); ok {
		in.Node = &p
	}
	id := s.SelectedPolicy()
	if id == 0 {
		return in, nil
	}
	p, _ := s.Policy(id)
	members := policyMembers(s, p)
	if len(members) == 0 {
		return in, fmt.Errorf(i18n.T("分组代理“%s”没有可用节点，请换一个分组代理或节点"), p.Name)
	}
	if in.Node == nil {
		in.Node = &members[0] // never chose a node: the first member stands in
	}
	in.Main = id
	if !slices.ContainsFunc(in.Policies, func(pi config.PolicyInput) bool { return pi.Policy.ID == id }) {
		in.Policies = append(in.Policies, config.PolicyInput{Policy: p, Members: members})
	}
	return in, nil
}

type policyRuntime struct {
	ID    int64            `json:"id"`
	Name  string           `json:"name"`
	Auto  bool             `json:"auto"`
	Tag   string           `json:"tag"`   // the group's outbound
	Nodes map[string]int64 `json:"nodes"` // member outbound → node ID
}

// PolicyRuntime describes the policy group the connection uses, as JSON;
// "" when it uses a node.
func PolicyRuntime() string {
	s, err := current()
	if err != nil {
		return ""
	}
	id := s.SelectedPolicy()
	if id == 0 {
		return ""
	}
	in, err := connectInput(s, phoneSettings(s.Settings()))
	if err != nil {
		return ""
	}
	_, nodeTags, policyTags := in.Tags()
	p, _ := s.Policy(id)
	r := policyRuntime{ID: id, Name: p.Name, Auto: p.Mode == store.PolicyAuto, Tag: policyTags[id], Nodes: map[string]int64{}}
	for _, pi := range in.Policies {
		if pi.Policy.ID != id {
			continue
		}
		for _, m := range pi.Members {
			r.Nodes[nodeTags[m.ID]] = m.ID
		}
	}
	b, _ := marshal(r)
	return b
}
