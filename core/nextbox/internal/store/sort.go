package store

import (
	"sort"
	"strings"
	"unicode"
	"unicode/utf8"
)

// The orders a group's nodes can be sorted by (Group.SortBy).
const (
	SortByName   = "name"
	SortByType   = "type"
	SortByServer = "server"
	SortByRegion = "region" // needs SetRegionFunc (NextBox for Android)
)

// Directions of a sort (Group.SortDir); "" is the group's normal order: the
// subscription's (manual groups: the order the nodes were added in).
const (
	SortAsc  = "asc"
	SortDesc = "desc"
)

// SetRegionFunc tells the store how to find a node's region, for
// SortByRegion. Without it, nodes are not sorted by region.
func (s *Store) SetRegionFunc(f func(Profile) string) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.regionOf = f
}

// SortGroup puts a group's nodes in an order and remembers it on the group:
// by one of the SortBy* keys, dir SortAsc or SortDesc; dir "" returns to the
// normal order (the key is still remembered, the apps show it). by
// "default" (or "") is the normal order with no key. Subscription updates
// sort again by what is remembered; reordering by hand forgets it.
func (s *Store) SortGroup(groupID int64, by, dir string) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	g := s.groupLocked(groupID)
	if by == "default" {
		by = ""
	}
	if dir != SortAsc && dir != SortDesc || by == "" {
		dir = ""
	}
	if g != nil {
		g.SortBy, g.SortDir = by, dir
		if g.URL != "" {
			g.ManualOrder = dir != ""
		}
	}
	var idx []int
	var items []*Profile
	for i, p := range s.data.Profiles {
		if p.GroupID == groupID {
			idx = append(idx, i)
			items = append(items, p)
		}
	}
	s.sortLocked(g, items, by, dir)
	for k, i := range idx {
		s.data.Profiles[i] = items[k]
	}
	return s.saveDataLocked()
}

// sortLocked orders the nodes of group g: the normal order first, then by
// the key, so that equal keys keep the normal order.
func (s *Store) sortLocked(g *Group, items []*Profile, by, dir string) {
	subPos := map[int64]int{}
	if g != nil {
		for i, id := range g.SubOrder {
			subPos[id] = i
		}
	}
	// the subscription's order, nodes added by hand after it as they were added
	sort.SliceStable(items, func(i, j int) bool {
		pi, iok := subPos[items[i].ID]
		pj, jok := subPos[items[j].ID]
		if iok != jok {
			return iok
		}
		if iok {
			return pi < pj
		}
		return items[i].ID < items[j].ID
	})
	if dir == "" {
		return
	}
	desc := dir == SortDesc
	var cmp func(a, b *Profile) int
	switch by {
	case SortByLatency:
		// reversed here, not below: untested and failed nodes stay last
		slowFirst := desc
		cmp = func(a, b *Profile) int {
			ka, kb := latencyKey(a.Latency), latencyKey(b.Latency)
			if slowFirst && a.Latency > 0 && b.Latency > 0 {
				ka, kb = -ka, -kb
			}
			return ka - kb
		}
		desc = false
	case SortByType:
		cmp = func(a, b *Profile) int { return strings.Compare(strings.ToLower(a.Type), strings.ToLower(b.Type)) }
	case SortByServer:
		cmp = func(a, b *Profile) int { return NaturalCompare(a.Server(), b.Server()) }
	case SortByRegion:
		if s.regionOf == nil {
			return
		}
		region := func(p *Profile) string {
			if r := s.regionOf(*p); r != "" {
				return r
			}
			return "￿" // not recognised: last
		}
		cmp = func(a, b *Profile) int { return strings.Compare(region(a), region(b)) }
	case SortByName:
		cmp = func(a, b *Profile) int { return NaturalCompare(a.Name, b.Name) }
	default:
		return
	}
	sort.SliceStable(items, func(i, j int) bool {
		if desc {
			return cmp(items[j], items[i]) < 0
		}
		return cmp(items[i], items[j]) < 0
	})
}

func latencyKey(l int) int {
	switch {
	case l > 0:
		return l
	case l == 0:
		return 1 << 30
	default:
		return 1<<30 + 1
	}
}

// NaturalCompare compares names the way people read them: runs of digits by
// their value ("日本2" before "日本10"), letters without case, and a flag
// emoji (or spaces) in front not at all.
func NaturalCompare(a, b string) int {
	a, b = trimName(a), trimName(b)
	for a != "" && b != "" {
		ra, _ := utf8.DecodeRuneInString(a)
		rb, _ := utf8.DecodeRuneInString(b)
		if isDigit(ra) && isDigit(rb) {
			na, nb := digits(a), digits(b)
			if c := compareNumbers(a[:na], b[:nb]); c != 0 {
				return c
			}
			a, b = a[na:], b[nb:]
			continue
		}
		la, lb := unicode.ToLower(ra), unicode.ToLower(rb)
		if la != lb {
			if la < lb {
				return -1
			}
			return 1
		}
		a, b = a[utf8.RuneLen(ra):], b[utf8.RuneLen(rb):]
	}
	switch {
	case a == "" && b == "":
		return 0
	case a == "":
		return -1
	default:
		return 1
	}
}

func isDigit(r rune) bool { return r >= '0' && r <= '9' }

func digits(s string) int {
	n := 0
	for n < len(s) && isDigit(rune(s[n])) {
		n++
	}
	return n
}

// compareNumbers compares two runs of digits by value.
func compareNumbers(a, b string) int {
	a, b = strings.TrimLeft(a, "0"), strings.TrimLeft(b, "0")
	if len(a) != len(b) {
		if len(a) < len(b) {
			return -1
		}
		return 1
	}
	return strings.Compare(a, b)
}

// trimName drops the flag emoji (two regional indicators) and the spaces a
// node name starts with.
func trimName(s string) string {
	for {
		s = strings.TrimLeftFunc(s, unicode.IsSpace)
		r, n := utf8.DecodeRuneInString(s)
		if r < 0x1F1E6 || r > 0x1F1FF {
			return s
		}
		s = s[n:]
	}
}

// SortAfterTest orders a group as Settings.SortAfterTest says once a test of
// the whole group is done: by latency, or back to the normal order.
func (s *Store) SortAfterTest(groupID int64, choice string) error {
	switch choice {
	case SortByLatency:
		return s.SortGroup(groupID, SortByLatency, SortAsc)
	case SortDefault:
		return s.SortGroup(groupID, "", "")
	}
	return nil
}
