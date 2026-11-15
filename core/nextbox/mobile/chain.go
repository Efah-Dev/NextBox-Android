package nbcore

import (
	"encoding/json"
	"errors"
	"fmt"
	"strings"

	"nextbox/internal/config"
	"nextbox/internal/geo"
	"nextbox/internal/i18n"
	"nextbox/internal/store"
)

// chainHop describes one member of a chain proxy for the UI.
type chainHop struct {
	ID      int64  `json:"id"`
	Name    string `json:"name"`
	Type    string `json:"type"`
	GroupID int64  `json:"groupId"`
	Missing bool   `json:"missing,omitempty"` // deleted, or renamed by a subscription update
}

// profileView is a node as the app sees it: a chain proxy also carries its
// members (which may live in other groups), and every node its region as far
// as it can be told.
type profileView struct {
	store.Profile
	Members []chainHop `json:"members,omitempty"`
	Region  string     `json:"region,omitempty"`
}

func viewOf(s *store.Store, p store.Profile) profileView {
	v := profileView{Profile: p, Region: regionOf(p)}
	if !p.IsChain() {
		return v
	}
	for i, m := range s.Members(p) {
		if m.ID == 0 {
			v.Members = append(v.Members, chainHop{ID: p.Chain[i], Missing: true})
			continue
		}
		v.Members = append(v.Members, chainHop{ID: m.ID, Name: m.Name, Type: m.Type, GroupID: m.GroupID})
	}
	if len(v.Members) > 0 {
		last := v.Members[len(v.Members)-1]
		if m, ok := s.Profile(last.ID); ok {
			v.Region = regionOf(m)
		}
	}
	return v
}

// regionOf is the ISO code of where a node is: the detected country when the
// desktop app found one, else a guess from its name ("" when unknown).
func regionOf(p store.Profile) string {
	if p.Country != "" {
		return p.Country
	}
	return geo.Guess(p.Name)
}

func sameOutbound(a, b map[string]any) bool {
	ja, _ := json.Marshal(a)
	jb, _ := json.Marshal(b)
	return string(ja) == string(jb)
}

// checkMembers validates the member list of a chain proxy.
func checkMembers(s *store.Store, ids []int64) error {
	if len(ids) < store.MinChainLength || len(ids) > store.MaxChainLength {
		return fmt.Errorf(i18n.T("链式代理需要 %d 到 %d 个节点"), store.MinChainLength, store.MaxChainLength)
	}
	seen := map[int64]bool{}
	for i, id := range ids {
		m, ok := s.Profile(id)
		switch {
		case !ok:
			return fmt.Errorf(i18n.T("第 %d 跳的节点已不存在，请替换"), i+1)
		case m.IsChain():
			return fmt.Errorf(i18n.T("第 %d 跳不能是另一个链式代理"), i+1)
		case seen[id]:
			return fmt.Errorf(i18n.T("“%s”在链路里出现了两次"), m.Name)
		}
		seen[id] = true
	}
	return nil
}

// chainError explains why a chain proxy cannot be used (nil for plain nodes).
func chainError(s *store.Store, p store.Profile) error {
	if !p.IsChain() {
		return nil
	}
	_, err := config.Expand(p, "check", s.Profile, nil)
	return err
}

type chainInput struct {
	ID      int64   `json:"id"`
	GroupID int64   `json:"groupId"` // -1 asks for a new manual group
	Name    string  `json:"name"`
	Chain   []int64 `json:"chain"`
}

// SaveChain creates or updates a chain proxy and returns it as JSON. It only
// lives in a manual group: a subscription update replaces the whole group,
// so a chain asked for in a subscription goes to the group "链式代理".
func SaveChain(js string) (string, error) {
	s, err := current()
	if err != nil {
		return "", err
	}
	var in chainInput
	if err := json.Unmarshal([]byte(js), &in); err != nil {
		return "", err
	}
	if err := checkMembers(s, in.Chain); err != nil {
		return "", err
	}
	name := strings.TrimSpace(in.Name)
	if name == "" {
		name = i18n.T("链式代理")
	}
	groupID := in.GroupID
	if g, ok := s.Group(groupID); groupID != -1 && (!ok || g.URL != "") {
		groupID = -1
	}
	if groupID == -1 {
		groupID = 0
		for _, g := range s.Groups() {
			if g.URL == "" && !g.Default && g.Name == i18n.T("链式代理") {
				groupID = g.ID
				break
			}
		}
		if groupID == 0 {
			g, err := s.AddGroup(store.Group{Name: i18n.T("链式代理")})
			if err != nil {
				return "", err
			}
			groupID = g.ID
		}
	}
	p := store.Profile{ID: in.ID, GroupID: groupID, Name: name, Type: store.TypeChain, Outbound: map[string]any{}, Chain: in.Chain}
	if in.ID != 0 {
		old, ok := s.Profile(in.ID)
		if !ok || !old.IsChain() {
			return "", errors.New(i18n.T("节点不存在"))
		}
		p.GroupID = old.GroupID
		p.Upload, p.Download = old.Upload, old.Download
		if sameJSON(old.Chain, in.Chain) {
			p.Latency, p.LatencyError, p.LatencyKind, p.TestedAt = old.Latency, old.LatencyError, old.LatencyKind, old.TestedAt
		}
	}
	saved, err := s.SaveProfile(p)
	if err != nil {
		return "", err
	}
	return marshal(viewOf(s, saved))
}

// ChainsUsing returns the names of the chain proxies that stop working when
// the given nodes (comma separated ids) are deleted, one per line.
func ChainsUsing(ids string) string {
	s, err := current()
	if err != nil {
		return ""
	}
	var names []string
	for _, p := range s.ChainsUsing(parseIDs(ids)) {
		names = append(names, p.Name)
	}
	return strings.Join(names, "\n")
}

func sameJSON(a, b any) bool {
	ja, _ := json.Marshal(a)
	jb, _ := json.Marshal(b)
	return string(ja) == string(jb)
}
