package nbcore

// The parts of a Clash subscription besides its nodes (proxy groups, rules,
// DNS settings), converted by internal/clash when the user chose to; as in
// the desktop app (clash.go there).

import (
	"context"
	"encoding/json"
	"errors"
	"sync"
	"time"

	"nextbox/internal/clash"
	"nextbox/internal/i18n"
	"nextbox/internal/store"
	"nextbox/internal/sub"
)

var (
	clashMu     sync.Mutex
	clashExtras = map[int64]*clash.Extras{} // the last Clash parts of each subscription
)

func setClashExtras(groupID int64, ex *clash.Extras) {
	clashMu.Lock()
	clashExtras[groupID] = ex
	clashMu.Unlock()
}

// clashExtrasOf returns the Clash parts of a subscription from its last
// update, or with fetch downloads it again (without touching its nodes).
func clashExtrasOf(g store.Group, fetch bool, proxy string) (*clash.Extras, error) {
	clashMu.Lock()
	ex := clashExtras[g.ID]
	clashMu.Unlock()
	if ex != nil || g.URL == "" || !fetch {
		return ex, nil
	}
	s, err := current()
	if err != nil {
		return nil, err
	}
	ua := g.UserAgent
	if ua == "" {
		ua = s.Settings().SubUserAgent
	}
	ctx, cancel := context.WithTimeout(context.Background(), 60*time.Second)
	defer cancel()
	res, err := sub.Fetch(ctx, g.URL, ua, proxy)
	if err != nil && proxy != "" {
		res, err = sub.Fetch(ctx, g.URL, ua, "")
	}
	if err != nil {
		return nil, err
	}
	if res.Clash != nil {
		setClashExtras(g.ID, res.Clash)
	}
	return res.Clash, nil
}

type clashInfo struct {
	Nodes   int                `json:"nodes"`
	Preview clash.Preview      `json:"preview"`
	Current *store.ClashImport `json:"current"`
}

// ClashPreview says (JSON) what the Clash parts of a subscription would
// make; "" when it has none. Without fetch only the last update is looked
// at (the question after adding or updating), with fetch the subscription is
// downloaded when needed. proxy as for UpdateSubscription.
func ClashPreview(groupID int64, fetch bool, proxy string) (string, error) {
	s, err := current()
	if err != nil {
		return "", err
	}
	g, ok := s.Group(groupID)
	if !ok {
		return "", errors.New(i18n.T("分组不存在"))
	}
	ex, err := clashExtrasOf(g, fetch, proxy)
	if err != nil || ex == nil {
		return "", err
	}
	nodes := s.Profiles(groupID)
	b, err := json.Marshal(clashInfo{Nodes: len(nodes), Preview: ex.Preview(nodes), Current: g.Clash})
	return string(b), err
}

// ApplyClash takes over the chosen parts (JSON store.ClashImport) of a Clash
// subscription, now and on every update, and says what it made.
func ApplyClash(groupID int64, options, proxy string) (string, error) {
	s, err := current()
	if err != nil {
		return "", err
	}
	g, ok := s.Group(groupID)
	if !ok {
		return "", errors.New(i18n.T("分组不存在"))
	}
	var opt store.ClashImport
	if err := json.Unmarshal([]byte(options), &opt); err != nil {
		return "", err
	}
	ex, err := clashExtrasOf(g, true, proxy)
	if err != nil {
		return "", err
	}
	// unticking the subscription's DNS stops using it
	if g.Clash != nil && g.Clash.SubDNS && !opt.SubDNS {
		g.DNS = nil
	}
	g.Clash = &opt
	if err := s.UpdateGroup(g); err != nil {
		return "", err
	}
	if ex == nil {
		return "", nil
	}
	sum, err := clash.Apply(s, ex, g, opt)
	if err != nil {
		return "", err
	}
	return sum.String(), nil
}
