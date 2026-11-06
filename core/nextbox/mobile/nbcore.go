// Package nbcore exposes NextBox's data store, share-link parser, subscription
// fetcher and config builder to the Android app through gomobile.
//
// Only gomobile-friendly types cross the boundary (string, bool, int32/int64,
// error, small interfaces); structured values travel as JSON in the same shape
// as the Windows data files, so backups are interchangeable.
package nbcore

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"sync"
	"time"

	"nextbox/internal/i18n"
	"nextbox/internal/link"
	"nextbox/internal/store"
	"nextbox/internal/sub"
)

var (
	mu      sync.Mutex
	st      *store.Store
	dataDir string
)

func current() (*store.Store, error) {
	mu.Lock()
	defer mu.Unlock()
	if st == nil {
		return nil, errors.New("nbcore: Open has not been called")
	}
	return st, nil
}

// Open loads (or creates) the data files in dir. A fresh install starts in
// VPN mode with every connection proxied ("global"); bypassing mainland China
// is opt-in on Android.
func Open(dir string) error {
	mu.Lock()
	defer mu.Unlock()
	if st != nil {
		return nil
	}
	_, statErr := os.Stat(filepath.Join(dir, "settings.json"))
	s, err := store.Open(dir)
	if err != nil {
		return err
	}
	if errors.Is(statErr, os.ErrNotExist) {
		set := s.Settings()
		set.RouteMode = "global"
		set.TunEnabled = true
		set.SystemProxy = false
		set.CloseToTray = false
		set.MixedPort = 2080
		if err := s.SaveSettings(set); err != nil {
			return err
		}
	}
	st = s
	dataDir = dir
	usageBook = loadUsage(filepath.Join(dir, "usage.json"))
	i18n.SetLang(s.Settings().Language)
	return nil
}

// SetTimeZone makes day boundaries of the usage statistics follow the phone's
// time zone (Go on Android otherwise runs in UTC).
func SetTimeZone(name string, offsetSeconds int32) {
	if loc, err := time.LoadLocation(name); err == nil {
		time.Local = loc
		return
	}
	time.Local = time.FixedZone(name, int(offsetSeconds))
}

// ---------------------------------------------------------------- state

type state struct {
	Groups         []store.Group  `json:"groups"`
	CurrentGroupID int64          `json:"currentGroupId"`
	SelectedID     int64          `json:"selectedId"`
	Settings       store.Settings `json:"settings"`
}

// State returns groups, the current group, the selected node and settings.
func State() (string, error) {
	s, err := current()
	if err != nil {
		return "", err
	}
	return marshal(state{
		Groups:         s.Groups(),
		CurrentGroupID: s.CurrentGroupID(),
		SelectedID:     s.SelectedID(),
		Settings:       s.Settings(),
	})
}

func SaveSettings(js string) error {
	s, err := current()
	if err != nil {
		return err
	}
	set := s.Settings()
	if err := json.Unmarshal([]byte(js), &set); err != nil {
		return err
	}
	if err := s.SaveSettings(set); err != nil {
		return err
	}
	i18n.SetLang(set.Language)
	return nil
}

// ---------------------------------------------------------------- groups

func SetCurrentGroup(id int64) error {
	s, err := current()
	if err != nil {
		return err
	}
	return s.SetCurrentGroup(id)
}

// AddGroup creates a manual group and makes it current.
func AddGroup(name string) (int64, error) {
	s, err := current()
	if err != nil {
		return 0, err
	}
	name = strings.TrimSpace(name)
	if name == "" {
		return 0, errors.New(i18n.T("请输入分组名称"))
	}
	g, err := s.AddGroup(store.Group{Name: name})
	if err != nil {
		return 0, err
	}
	return g.ID, s.SetCurrentGroup(g.ID)
}

// EditGroup updates the name, subscription URL and user agent of a group.
func EditGroup(id int64, name, url, userAgent string) error {
	s, err := current()
	if err != nil {
		return err
	}
	g, ok := s.Group(id)
	if !ok {
		return errors.New(i18n.T("分组不存在"))
	}
	g.Name = strings.TrimSpace(name)
	g.URL = strings.TrimSpace(url)
	g.UserAgent = strings.TrimSpace(userAgent)
	return s.UpdateGroup(g)
}

func DeleteGroup(id int64) error {
	s, err := current()
	if err != nil {
		return err
	}
	return s.DeleteGroup(id)
}

// AddSubscription adds a subscription group and downloads it. proxy is an
// optional "http://127.0.0.1:port" used when the core is running.
func AddSubscription(name, rawURL, proxy string) (string, error) {
	s, err := current()
	if err != nil {
		return "", err
	}
	rawURL = strings.TrimSpace(rawURL)
	if !strings.HasPrefix(rawURL, "http://") && !strings.HasPrefix(rawURL, "https://") {
		return "", errors.New(i18n.T("订阅地址必须以 http:// 或 https:// 开头"))
	}
	if strings.TrimSpace(name) == "" {
		name = i18n.T("新订阅")
	}
	g, err := s.AddGroup(store.Group{Name: strings.TrimSpace(name), URL: rawURL})
	if err != nil {
		return "", err
	}
	s.SetCurrentGroup(g.ID)
	return UpdateSubscription(g.ID, proxy)
}

// UpdateSubscription downloads a subscription group again. Nodes that keep
// their name and type keep their ID, selection and traffic statistics.
func UpdateSubscription(groupID int64, proxy string) (string, error) {
	s, err := current()
	if err != nil {
		return "", err
	}
	g, ok := s.Group(groupID)
	if !ok {
		return "", errors.New(i18n.T("分组不存在"))
	}
	if g.URL == "" {
		return "", errors.New(i18n.T("这个分组不是订阅"))
	}
	ua := g.UserAgent
	if ua == "" {
		ua = s.Settings().SubUserAgent
	}
	ctx, cancel := context.WithTimeout(context.Background(), 60*time.Second)
	defer cancel()
	res, err := sub.Fetch(ctx, g.URL, ua, proxy)
	if err != nil && proxy != "" {
		// the node may be the problem: try once more without the proxy
		res, err = sub.Fetch(ctx, g.URL, ua, "")
	}
	if err != nil {
		return "", err
	}
	profiles := make([]store.Profile, 0, len(res.Nodes))
	for _, n := range res.Nodes {
		profiles = append(profiles, store.Profile{Name: n.Name, Type: n.Type(), Outbound: n.Outbound})
	}
	if err := s.ReplaceGroupProfiles(groupID, profiles); err != nil {
		return "", err
	}
	g, _ = s.Group(groupID)
	g.UpdatedAt = time.Now().Unix()
	if res.Usage != nil {
		g.Usage = res.Usage
	}
	if err := s.UpdateGroup(g); err != nil {
		return "", err
	}
	return fmt.Sprintf(i18n.T("“%s”更新完成：%d 个节点"), g.Name, len(profiles)) + skipNote(res.Skipped), nil
}

// ---------------------------------------------------------------- profiles

// Profiles lists the nodes of a group (0 = all groups).
func Profiles(groupID int64) (string, error) {
	s, err := current()
	if err != nil {
		return "", err
	}
	return marshal(s.Profiles(groupID))
}

func Profile(id int64) (string, error) {
	s, err := current()
	if err != nil {
		return "", err
	}
	p, ok := s.Profile(id)
	if !ok {
		return "", errors.New(i18n.T("节点不存在"))
	}
	return marshal(p)
}

func SetSelected(id int64) error {
	s, err := current()
	if err != nil {
		return err
	}
	return s.SetSelected(id)
}

// ImportText imports share links or subscription content into a group.
func ImportText(text string, groupID int64) (string, error) {
	s, err := current()
	if err != nil {
		return "", err
	}
	nodes, skipped, err := link.ParseContent(text)
	if err != nil {
		return "", err
	}
	list := make([]store.Profile, 0, len(nodes))
	for _, n := range nodes {
		list = append(list, store.Profile{Name: n.Name, Type: n.Type(), Outbound: n.Outbound})
	}
	added, err := s.AddProfiles(groupID, list)
	if err != nil {
		return "", err
	}
	if len(added) > 0 && s.SelectedID() == 0 {
		s.SetSelected(added[0].ID)
	}
	return fmt.Sprintf(i18n.T("已导入 %d 个节点"), len(list)) + skipNote(skipped), nil
}

// SaveProfile inserts (id 0) or replaces a node and returns the saved JSON.
// Latency and traffic statistics of an existing node are kept.
func SaveProfile(js string) (string, error) {
	s, err := current()
	if err != nil {
		return "", err
	}
	var p store.Profile
	if err := json.Unmarshal([]byte(js), &p); err != nil {
		return "", err
	}
	typ, _ := p.Outbound["type"].(string)
	if typ == "" {
		return "", errors.New(i18n.T("出站配置缺少 type 字段"))
	}
	delete(p.Outbound, "tag")
	p.Type = typ
	p.Name = strings.TrimSpace(p.Name)
	if p.Name == "" {
		p.Name = typ
	}
	if p.ID != 0 {
		if old, ok := s.Profile(p.ID); ok {
			p.Latency, p.LatencyError, p.TestedAt = old.Latency, old.LatencyError, old.TestedAt
			p.Upload, p.Download = old.Upload, old.Download
		}
	}
	saved, err := s.SaveProfile(p)
	if err != nil {
		return "", err
	}
	return marshal(saved)
}

// DeleteProfiles deletes nodes; ids is a comma separated list.
func DeleteProfiles(ids string) error {
	s, err := current()
	if err != nil {
		return err
	}
	return s.DeleteProfiles(parseIDs(ids))
}

// SortGroup sorts a group by "name", "type" or "latency".
func SortGroup(groupID int64, by string) error {
	s, err := current()
	if err != nil {
		return err
	}
	return s.SortGroup(groupID, by)
}

// ExportLinks returns the share links of the given nodes, one per line.
func ExportLinks(ids string) (string, error) {
	s, err := current()
	if err != nil {
		return "", err
	}
	var lines []string
	for _, p := range s.ProfilesByIDs(parseIDs(ids)) {
		l, err := link.Export(p.Name, p.Outbound)
		if err != nil {
			return "", fmt.Errorf("%s: %w", p.Name, err)
		}
		lines = append(lines, l)
	}
	return strings.Join(lines, "\n"), nil
}

// ---------------------------------------------------------------- backup

type backup struct {
	App      string          `json:"app"`
	Version  int             `json:"version"`
	Profiles json.RawMessage `json:"profiles"`
	Settings json.RawMessage `json:"settings"`
}

// Backup returns profiles.json and settings.json bundled in one JSON document.
func Backup() (string, error) {
	s, err := current()
	if err != nil {
		return "", err
	}
	if err := Flush(); err != nil {
		return "", err
	}
	profiles, err := os.ReadFile(filepath.Join(s.Dir, "profiles.json"))
	if err != nil {
		return "", err
	}
	settings, err := os.ReadFile(filepath.Join(s.Dir, "settings.json"))
	if err != nil {
		return "", err
	}
	return marshal(backup{App: "NextBox", Version: 1, Profiles: profiles, Settings: settings})
}

// Restore replaces all data with a backup made by Backup. Plain Windows
// profiles.json content is accepted as well (settings are then kept).
func Restore(js string) error {
	s, err := current()
	if err != nil {
		return err
	}
	var b backup
	if err := json.Unmarshal([]byte(js), &b); err != nil {
		return err
	}
	profiles, settings := b.Profiles, b.Settings
	if len(profiles) == 0 {
		var probe struct {
			Profiles []json.RawMessage `json:"profiles"`
		}
		if json.Unmarshal([]byte(js), &probe) != nil || probe.Profiles == nil {
			return errors.New(i18n.T("不是有效的备份文件"))
		}
		profiles = json.RawMessage(js)
	}
	dir := s.Dir
	if err := os.WriteFile(filepath.Join(dir, "profiles.json"), profiles, 0o644); err != nil {
		return err
	}
	if len(settings) > 0 {
		if err := os.WriteFile(filepath.Join(dir, "settings.json"), settings, 0o644); err != nil {
			return err
		}
	}
	reopened, err := store.Open(dir)
	if err != nil {
		return err
	}
	mu.Lock()
	st = reopened
	mu.Unlock()
	return nil
}

// ---------------------------------------------------------------- helpers

func marshal(v any) (string, error) {
	b, err := json.Marshal(v)
	return string(b), err
}

func parseIDs(s string) []int64 {
	var out []int64
	for _, part := range strings.Split(s, ",") {
		if id, err := strconv.ParseInt(strings.TrimSpace(part), 10, 64); err == nil {
			out = append(out, id)
		}
	}
	return out
}

func skipNote(skipped []string) string {
	if len(skipped) == 0 {
		return ""
	}
	return fmt.Sprintf(i18n.T("，跳过 %d 条（%s）"), len(skipped), skipped[0])
}
