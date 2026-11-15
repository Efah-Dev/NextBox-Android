package nbcore

// Backups, in the format NextBox for Windows (2.0+) reads and writes:
// {"app":"NextBox","version":2,"platform":"android","profiles":<profiles.json>,
// "settings":<settings.json>}, sealed with a password in the envelope of
// internal/vault. Settings that only mean something on one system (ports,
// TUN, autostart, program rules) stay there. Android-only preferences
// (selected apps, the route scope) travel under "android", which the desktop
// app ignores.

import (
	"encoding/json"
	"errors"
	"fmt"
	"strings"

	"nextbox/internal/i18n"
	"nextbox/internal/store"
	"nextbox/internal/vault"
)

const backupPlatform = "android"

type backupDoc struct {
	App      string          `json:"app"`
	Version  int             `json:"version"`
	Platform string          `json:"platform,omitempty"`
	Profiles json.RawMessage `json:"profiles,omitempty"`
	Settings json.RawMessage `json:"settings,omitempty"`
	Android  json.RawMessage `json:"android,omitempty"`
}

// Settings that mean the same on every platform (see the desktop app).
var portableSettings = []string{
	"theme", "accentColor", "language", "lightLevel", "darkLevel", "recentColors",
	"remoteDns", "directDns", "enableIpv6", "sniff",
	"testUrl", "testTimeout", "testConcurrency",
	"subUserAgent", "subUpdateByProxy", "subAutoUpdate", "subUpdateHours",
	"countrySource", "autoCheckUpdate",
}

// ExportBackup makes a backup of everything: nodes, rules and settings, plus
// the Android-only preferences (android, a JSON object). It is sealed with
// password unless that is empty.
func ExportBackup(password, android string) (string, error) {
	s, err := current()
	if err != nil {
		return "", err
	}
	if err := s.Flush(); err != nil {
		return "", err
	}
	doc := backupDoc{App: "NextBox", Version: 2, Platform: backupPlatform}
	if doc.Profiles, err = s.ExportData(); err != nil {
		return "", err
	}
	if doc.Settings, err = json.Marshal(s.Settings()); err != nil {
		return "", err
	}
	if json.Valid([]byte(android)) {
		doc.Android = json.RawMessage(android)
	}
	plain, err := json.MarshalIndent(doc, "", "  ")
	if err != nil {
		return "", err
	}
	if password == "" {
		return string(plain), nil
	}
	out, err := vault.Encrypt(plain, password)
	return string(out), err
}

// readBackup opens a backup (or a plain profiles.json). encrypted is true for
// a sealed file; with no password given the document stays empty.
func readBackup(content, password string) (doc backupDoc, encrypted bool, err error) {
	b := []byte(strings.TrimSpace(strings.TrimPrefix(content, string(rune(0xFEFF)))))
	if env, ok := vault.Parse(b); ok {
		encrypted = true
		if env.KDF == vault.KDFWindows {
			return doc, true, errors.New(i18n.T("这是 Windows 版本机加密保存的 profiles.json，不是备份文件：请在电脑上用「导出配置」生成备份"))
		}
		if password == "" {
			return doc, true, nil
		}
		plain, _, err := vault.Decrypt(env, password)
		if errors.Is(err, vault.ErrPassword) {
			return doc, true, errors.New(i18n.T("密码不对，或者文件已损坏"))
		}
		if err != nil {
			return doc, true, err
		}
		b = plain
	}
	if err := json.Unmarshal(b, &doc); err != nil {
		return doc, encrypted, errors.New(i18n.T("不是有效的备份文件"))
	}
	if len(doc.Profiles) == 0 && len(doc.Settings) == 0 {
		// a bare profiles.json
		var probe struct {
			Profiles []json.RawMessage `json:"profiles"`
		}
		if json.Unmarshal(b, &probe) != nil || probe.Profiles == nil {
			return doc, encrypted, errors.New(i18n.T("不是有效的备份文件"))
		}
		doc = backupDoc{App: "NextBox", Profiles: b}
	}
	if doc.Platform == "" && doc.Version == 1 {
		doc.Platform = backupPlatform // only the Android version wrote version 1
	}
	return doc, encrypted, nil
}

// backupRules reads the rules of a backup as Android uses them: the desktop
// app's scenes become rules and program rules ("chrome.exe") are dropped.
// skipped counts the dropped program names.
func backupRules(raw json.RawMessage) (set store.Settings, skipped int, err error) {
	set = store.DefaultSettings()
	set.Rules = nil
	if err := json.Unmarshal(raw, &set); err != nil {
		return set, 0, errors.New(i18n.T("备份里的设置已损坏"))
	}
	set.Normalize()
	kept := set.Rules[:0]
	for _, r := range set.Rules {
		if r.Kind == "process" {
			skipped += len(r.Values)
			continue
		}
		kept = append(kept, r)
	}
	set.Rules = kept
	scenesToRules(&set)
	return set, skipped, nil
}

type backupInfo struct {
	Encrypted    bool              `json:"encrypted"`
	NeedPassword bool              `json:"needPassword"`
	Platform     string            `json:"platform"` // "windows", "android" or ""
	HasNodes     bool              `json:"hasNodes"`
	HasSettings  bool              `json:"hasSettings"`
	Summary      store.DataSummary `json:"summary"`
	Rules        int               `json:"rules"`
	Skipped      int               `json:"skipped"` // program rules a phone cannot use
}

// InspectBackup describes a backup before it is imported. An encrypted one
// without password comes back with needPassword set.
func InspectBackup(content, password string) (string, error) {
	doc, encrypted, err := readBackup(content, password)
	if err != nil {
		return "", err
	}
	info := backupInfo{Encrypted: encrypted}
	if encrypted && password == "" {
		info.NeedPassword = true
		return marshal(info)
	}
	info.Platform = doc.Platform
	if len(doc.Profiles) > 0 {
		sum, err := store.SummarizeData(doc.Profiles)
		if err != nil {
			return "", errors.New(i18n.T("备份里的节点数据已损坏"))
		}
		info.HasNodes, info.Summary = true, sum
	}
	if len(doc.Settings) > 0 {
		set, skipped, err := backupRules(doc.Settings)
		if err != nil {
			return "", err
		}
		info.HasSettings, info.Rules, info.Skipped = true, len(set.Rules), skipped
	}
	return marshal(info)
}

type importResult struct {
	Message string          `json:"message"`
	Android json.RawMessage `json:"android,omitempty"` // Android preferences to apply
}

// ImportBackup imports everything a backup holds. replace replaces the nodes
// and rules; otherwise they are added to what is there.
func ImportBackup(content, password string, replace bool) (string, error) {
	s, err := current()
	if err != nil {
		return "", err
	}
	doc, encrypted, err := readBackup(content, password)
	if err != nil {
		return "", err
	}
	if encrypted && password == "" {
		return "", errors.New(i18n.T("请输入备份密码"))
	}
	var done []string
	var res importResult
	policyMap := map[int64]int64{}
	if len(doc.Profiles) > 0 {
		m, err := s.ImportData(doc.Profiles, replace)
		if err != nil {
			return "", fmt.Errorf(i18n.T("导入节点失败: %w"), err)
		}
		policyMap = m
		sum, _ := store.SummarizeData(doc.Profiles)
		done = append(done, fmt.Sprintf(i18n.T("%d 个节点"), sum.Nodes+sum.Chains))
	}
	if len(doc.Settings) > 0 {
		in, _, err := backupRules(doc.Settings)
		if err != nil {
			return "", err
		}
		remapPolicies(&in, policyMap)
		var keys []string // nil: every setting of an Android backup
		if doc.Platform != backupPlatform {
			keys = portableSettings
		}
		cur := s.Settings()
		next, err := mergeSettings(cur, doc.Settings, keys)
		if err != nil {
			return "", err
		}
		// the routing part, the way Android keeps it
		next.BlockAds = in.BlockAds
		next.Scenes = in.Scenes
		next.RouteMode = "rule"
		if in.RouteMode == "direct" {
			next.RouteMode = "direct"
		}
		if in.RouteMode == "global" { // the desktop app's global mode means "no bypass"
			for i := range next.Scenes {
				if next.Scenes[i].ID == "cn" {
					next.Scenes[i].Enabled = false
				}
			}
		}
		if replace {
			next.Rules = in.Rules
		} else {
			next.Rules = append(cur.Rules, newRules(cur.Rules, in.Rules)...)
		}
		next.Rules = sortRules(next.Rules)
		if doc.Platform != backupPlatform {
			// keep this phone's own platform settings whatever the file says
			next.TunEnabled, next.MixedPort, next.ListenAddress, next.AllowLAN = cur.TunEnabled, cur.MixedPort, cur.ListenAddress, cur.AllowLAN
			next.SystemProxy, next.CloseToTray, next.AutoStart = false, false, false
		}
		if err := s.SaveSettings(next); err != nil {
			return "", err
		}
		done = append(done, i18n.T("分流规则"), i18n.T("设置"))
		if len(doc.Android) > 0 && json.Valid(doc.Android) {
			res.Android = doc.Android
		}
	}
	if len(done) == 0 {
		return "", errors.New(i18n.T("备份里没有可以导入的内容"))
	}
	s.Flush()
	res.Message = i18n.T("已导入：") + strings.Join(done, i18n.T("、"))
	return marshal(res)
}

// newRules returns the rules of in that have is missing.
func newRules(have, in []store.Rule) []store.Rule {
	seen := map[string]bool{}
	for _, r := range have {
		b, _ := json.Marshal(r)
		seen[string(b)] = true
	}
	var out []store.Rule
	for _, r := range in {
		b, _ := json.Marshal(r)
		if !seen[string(b)] {
			seen[string(b)] = true
			out = append(out, r)
		}
	}
	return out
}

// mergeSettings applies the given keys of an imported settings document (all
// of them for keys == nil).
func mergeSettings(cur store.Settings, imported json.RawMessage, keys []string) (store.Settings, error) {
	var in map[string]json.RawMessage
	if err := json.Unmarshal(imported, &in); err != nil {
		return cur, errors.New(i18n.T("备份里的设置已损坏"))
	}
	b, _ := json.Marshal(cur)
	var out map[string]json.RawMessage
	json.Unmarshal(b, &out)
	for k, v := range in {
		if keys == nil || contains(keys, k) {
			out[k] = v
		}
	}
	b, _ = json.Marshal(out)
	next := store.DefaultSettings()
	if err := json.Unmarshal(b, &next); err != nil {
		return cur, errors.New(i18n.T("备份里的设置已损坏"))
	}
	next.Normalize()
	return next, nil
}

// remapPolicies points imported rules at the policy groups as they were
// imported; rules naming a group that did not come along use the node.
func remapPolicies(s *store.Settings, m map[int64]int64) {
	for i, r := range s.Rules {
		if id := store.TargetPolicy(r.Target); id != 0 {
			if n, ok := m[id]; ok {
				s.Rules[i].Target = store.PolicyTarget(n)
			} else {
				s.Rules[i].Target = store.TargetProxy
			}
		}
	}
}

func contains(list []string, s string) bool {
	for _, v := range list {
		if v == s {
			return true
		}
	}
	return false
}
