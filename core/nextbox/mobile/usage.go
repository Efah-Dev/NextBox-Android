package nbcore

import (
	"encoding/json"
	"os"
	"sort"
	"sync"
	"time"
)

// Traffic statistics: per node totals live in the store (Profile.Upload /
// Download, shared with Windows), per day totals in usage.json.

type traffic struct {
	Up   int64 `json:"up"`
	Down int64 `json:"down"`
}

type usage struct {
	mu    sync.Mutex
	path  string
	dirty bool
	Days  map[string]*traffic `json:"days"` // "2006-01-02" local date
}

var usageBook *usage

const keepDays = 400

func loadUsage(path string) *usage {
	u := &usage{path: path, Days: map[string]*traffic{}}
	if b, err := os.ReadFile(path); err == nil {
		json.Unmarshal(b, u)
		if u.Days == nil {
			u.Days = map[string]*traffic{}
		}
	}
	return u
}

func (u *usage) add(day string, up, down int64) {
	u.mu.Lock()
	defer u.mu.Unlock()
	t := u.Days[day]
	if t == nil {
		t = &traffic{}
		u.Days[day] = t
	}
	t.Up += up
	t.Down += down
	u.dirty = true
}

func (u *usage) save() error {
	u.mu.Lock()
	defer u.mu.Unlock()
	if !u.dirty {
		return nil
	}
	cutoff := time.Now().AddDate(0, 0, -keepDays).Format(time.DateOnly)
	for d := range u.Days {
		if d < cutoff {
			delete(u.Days, d)
		}
	}
	b, err := json.Marshal(u)
	if err != nil {
		return err
	}
	tmp := u.path + ".tmp"
	if err := os.WriteFile(tmp, b, 0o644); err != nil {
		return err
	}
	if err := os.Rename(tmp, u.path); err != nil {
		return err
	}
	u.dirty = false
	return nil
}

// AddTraffic records bytes moved since the last call. dayUp / dayDown (all
// traffic through NextBox) go to the day total; nodeUp / nodeDown (traffic
// that actually went through the proxy) go to the node. Call Flush to persist.
func AddTraffic(nodeID, nodeUp, nodeDown, dayUp, dayDown int64) {
	if s, err := current(); err == nil && nodeID != 0 && (nodeUp > 0 || nodeDown > 0) {
		s.AddTraffic(nodeID, nodeUp, nodeDown)
	}
	if usageBook != nil && (dayUp > 0 || dayDown > 0) {
		usageBook.add(time.Now().Format(time.DateOnly), dayUp, dayDown)
	}
}

// Flush persists node traffic, latency results and day totals.
func Flush() error {
	s, err := current()
	if err != nil {
		return err
	}
	if usageBook != nil {
		usageBook.save()
	}
	return s.Flush()
}

type dayItem struct {
	Date string `json:"date"`
	traffic
}

type nodeItem struct {
	ID    int64  `json:"id"`
	Name  string `json:"name"`
	Type  string `json:"type"`
	Group string `json:"group"`
	traffic
}

type usageReport struct {
	Today      traffic    `json:"today"`
	Period     traffic    `json:"period"`
	PeriodFrom string     `json:"periodFrom"` // first day of the billing period
	Days       []dayItem  `json:"days"`       // the last 30 days, oldest first
	Nodes      []nodeItem `json:"nodes"`      // nodes with traffic, most used first
}

// Usage returns the traffic report. billingDay (1-28) is the first day of the
// monthly period; 1 means calendar months.
func Usage(billingDay int32) (string, error) {
	s, err := current()
	if err != nil {
		return "", err
	}
	if billingDay < 1 || billingDay > 28 {
		billingDay = 1
	}
	now := time.Now()
	from := time.Date(now.Year(), now.Month(), int(billingDay), 0, 0, 0, 0, time.Local)
	if now.Before(from) {
		from = from.AddDate(0, -1, 0)
	}
	rep := usageReport{PeriodFrom: from.Format(time.DateOnly), Days: []dayItem{}, Nodes: []nodeItem{}}
	today := now.Format(time.DateOnly)
	fromKey := rep.PeriodFrom
	if usageBook != nil {
		usageBook.mu.Lock()
		for d, t := range usageBook.Days {
			if d == today {
				rep.Today = *t
			}
			if d >= fromKey && d <= today {
				rep.Period.Up += t.Up
				rep.Period.Down += t.Down
			}
		}
		for i := 29; i >= 0; i-- {
			d := now.AddDate(0, 0, -i).Format(time.DateOnly)
			item := dayItem{Date: d}
			if t := usageBook.Days[d]; t != nil {
				item.traffic = *t
			}
			rep.Days = append(rep.Days, item)
		}
		usageBook.mu.Unlock()
	}
	groups := map[int64]string{}
	for _, g := range s.Groups() {
		groups[g.ID] = g.Name
	}
	for _, p := range s.Profiles(0) {
		if p.Upload+p.Download == 0 {
			continue
		}
		rep.Nodes = append(rep.Nodes, nodeItem{
			ID: p.ID, Name: p.Name, Type: p.Type, Group: groups[p.GroupID],
			traffic: traffic{Up: p.Upload, Down: p.Download},
		})
	}
	sort.Slice(rep.Nodes, func(i, j int) bool {
		return rep.Nodes[i].Up+rep.Nodes[i].Down > rep.Nodes[j].Up+rep.Nodes[j].Down
	})
	return marshal(rep)
}

// ResetUsage clears the day totals and every node's traffic counters.
func ResetUsage() error {
	s, err := current()
	if err != nil {
		return err
	}
	for _, p := range s.Profiles(0) {
		if p.Upload != 0 || p.Download != 0 {
			s.AddTraffic(p.ID, -p.Upload, -p.Download)
		}
	}
	if usageBook != nil {
		usageBook.mu.Lock()
		usageBook.Days = map[string]*traffic{}
		usageBook.dirty = true
		usageBook.mu.Unlock()
	}
	return Flush()
}
