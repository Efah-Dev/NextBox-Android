package nbcore

// Backups, in the format NextBox for Windows (2.0+) reads and writes:
// {"app":"NextBox","version":2,"platform":"android","profiles":<profiles.json>,
// "settings":<settings.json>,"android":{…}}, sealed with a password in the
// envelope of internal/vault. It mirrors the desktop app's backup.go: the
// same three parts (nodes, routing rules, other settings), the same keys,
// and the same rules for a backup from the other system: only the settings
// that mean the same everywhere come along, the home page's routing choice
// stays, and rules the other system cannot use (programs there, apps here)
// are kept as they are so that nothing is lost on the way back.
// Android-only preferences (selected apps and the like) travel under
// "android", which the desktop app keeps without reading.

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
	// Route is 3 from Android 2.0 on, whose rules are checked in their stored
	// order (as on the desktop). Android 1.x routed by a fixed path instead,
	// so rules from its backups are put in that path's order on import.
	Route int `json:"route,omitempty"`
}

// The look of the app (外观), a part of its own (as in the desktop app): a
// backup takes it along by default, also to and from the other system.
var appearanceSettings = []string{
	"theme", "accentColor", "language", "lightLevel", "darkLevel", "themeScale", "recentColors",
}

// Other settings that mean the same on every platform (as in the desktop app).
var portableSettings = []string{
	"remoteDns", "directDns", "enableIpv6", "sniff",
	"testUrl", "testTimeout", "testConcurrency",
	"subUserAgent", "subUpdateByProxy", "subAutoUpdate", "subUpdateHours",
	"countrySource", "autoCheckUpdate",
}

// Settings that make up the routing rules (分流规则), as in the desktop app.
var ruleSettings = []string{
	"routeMode", "blockAds", "rules", "scenes", "ruleSetMirror", "bypassDns",
	"routeView", "routeHideOff", "routeViewAsked", // how the desktop page looks
	"directRules", "proxyRules", "blockRules", // v1.2 and Android 1.0
}

// backupOptions says what goes into a backup or is taken from one.
type backupOptions struct {
	Nodes      bool   `json:"nodes"`      // groups, nodes, chain proxies, policy groups
	Rules      bool   `json:"rules"`      // routing rules
	Appearance bool   `json:"appearance"` // theme, colours, language, with the Android ones
	Settings   bool   `json:"settings"`   // the other settings, with the Android preferences
	Password   string `json:"password"`   // "" = not encrypted (export) / not given yet (import)
	// Import only: replace the node list instead of adding to it.
	Replace bool `json:"replace"`
}

// ExportBackup makes a backup of the parts options (a backupOptions JSON)
// asks for; android is a JSON object of the Android-only preferences, saved
// with the other settings. It is sealed with the password unless that is
// empty.
func ExportBackup(options, android string) (string, error) {
	var o backupOptions
	if err := json.Unmarshal([]byte(options), &o); err != nil {
		return "", err
	}
	if !o.Nodes && !o.Rules && !o.Appearance && !o.Settings {
		return "", errors.New(i18n.T("请至少选择一项内容"))
	}
	s, err := current()
	if err != nil {
		return "", err
	}
	if err := s.Flush(); err != nil {
		return "", err
	}
	doc := backupDoc{App: "NextBox", Version: 2, Platform: backupPlatform, Route: routeOrdered}
	if o.Nodes {
		if doc.Profiles, err = s.ExportData(); err != nil {
			return "", err
		}
	}
	if o.Rules || o.Appearance || o.Settings {
		raw, err := json.Marshal(s.Settings())
		if err != nil {
			return "", err
		}
		raw, err = keepParts(raw, o)
		if err != nil {
			return "", err
		}
		doc.Settings = raw
	}
	// the Android preferences of the chosen parts (see Prefs.exportJson)
	if (o.Appearance || o.Settings) && json.Valid([]byte(android)) {
		doc.Android = json.RawMessage(android)
	}
	plain, err := json.MarshalIndent(doc, "", "  ")
	if err != nil {
		return "", err
	}
	if o.Password == "" {
		return string(plain), nil
	}
	out, err := vault.Encrypt(plain, o.Password)
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

type backupInfo struct {
	Encrypted    bool   `json:"encrypted"`
	NeedPassword bool   `json:"needPassword"`
	Platform     string `json:"platform"` // "windows", "android" or ""
	Version      int    `json:"version"`
	HasNodes     bool   `json:"hasNodes"`
	HasRules     bool   `json:"hasRules"`
	HasSettings  bool   `json:"hasSettings"` // settings other than the rules and the look
	// HasAppearance: the backup has the look of the app (backups made before
	// it was a part of its own have it with the other settings).
	HasAppearance bool              `json:"hasAppearance"`
	Summary       store.DataSummary `json:"summary"`
	Rules         int               `json:"rules"`
	// DesktopRules counts the rules by program ("chrome.exe"), which only
	// the desktop app can use; they are kept anyway.
	DesktopRules int `json:"desktopRules"`
	// Scenes lists the desktop app's ready-made scenes that are on ("ai",
	// "media", "games"); they become rules here.
	Scenes []string `json:"scenes"`
}

// InspectBackup describes a backup before it is imported. An encrypted one
// without password comes back with needPassword set.
func InspectBackup(content, password string) (string, error) {
	doc, encrypted, err := readBackup(content, password)
	if err != nil {
		return "", err
	}
	info := backupInfo{Encrypted: encrypted, Scenes: []string{}}
	if encrypted && password == "" {
		info.NeedPassword = true
		return marshal(info)
	}
	info.Platform, info.Version = doc.Platform, doc.Version
	if len(doc.Profiles) > 0 {
		sum, err := store.SummarizeData(doc.Profiles)
		if err != nil {
			return "", errors.New(i18n.T("备份里的节点数据已损坏"))
		}
		info.HasNodes, info.Summary = true, sum
	}
	if len(doc.Settings) > 0 {
		var keys map[string]json.RawMessage
		if json.Unmarshal(doc.Settings, &keys) != nil {
			return "", errors.New(i18n.T("备份里的设置已损坏"))
		}
		for k := range keys {
			switch settingsPart(k) {
			case "rules":
				info.HasRules = true
			case "appearance":
				info.HasAppearance = true
			default:
				info.HasSettings = true
			}
		}
		set := store.DefaultSettings()
		set.Rules = nil
		if json.Unmarshal(doc.Settings, &set) == nil {
			set.Normalize()
			for _, r := range set.Rules {
				switch {
				case r.IsStep():
				case r.Kind == "process":
					info.DesktopRules++
					info.Rules++
				default:
					info.Rules++
				}
			}
			if _, ok := keys["scenes"]; ok {
				for _, sc := range set.Scenes {
					if sc.Enabled && sc.ID != "cn" {
						info.Scenes = append(info.Scenes, sc.ID)
					}
				}
			}
		}
	}
	return marshal(info)
}

type importResult struct {
	Message string          `json:"message"`
	Android json.RawMessage `json:"android,omitempty"` // Android preferences to apply
}

// ImportBackup imports the parts of a backup that options (a backupOptions
// JSON) asks for. Nodes are added to the list or replace it; the rules of
// the backup replace the current ones, as in the desktop app.
func ImportBackup(content, options string) (string, error) {
	var o backupOptions
	if err := json.Unmarshal([]byte(options), &o); err != nil {
		return "", err
	}
	s, err := current()
	if err != nil {
		return "", err
	}
	doc, encrypted, err := readBackup(content, o.Password)
	if err != nil {
		return "", err
	}
	if encrypted && o.Password == "" {
		return "", errors.New(i18n.T("请输入备份密码"))
	}
	var done []string
	var res importResult
	policyMap := map[int64]int64{}
	if o.Nodes && len(doc.Profiles) > 0 {
		m, err := s.ImportData(doc.Profiles, o.Replace)
		if err != nil {
			return "", fmt.Errorf(i18n.T("导入节点失败: %w"), err)
		}
		policyMap = m
		sum, _ := store.SummarizeData(doc.Profiles)
		done = append(done, fmt.Sprintf(i18n.T("%d 个节点"), sum.Nodes+sum.Chains))
	}
	if len(doc.Settings) > 0 {
		// only the parts the backup has: a backup of the other settings must
		// not clear the rules here (nor turn ad blocking off)
		var in map[string]json.RawMessage
		if json.Unmarshal(doc.Settings, &in) != nil {
			return "", errors.New(i18n.T("备份里的设置已损坏"))
		}
		has := map[string]bool{}
		for k := range in {
			has[settingsPart(k)] = true
		}
		o.Rules = o.Rules && has["rules"]
		o.Appearance = o.Appearance && has["appearance"]
		o.Settings = o.Settings && has["settings"]
	}
	if (o.Rules || o.Appearance || o.Settings) && len(doc.Settings) > 0 {
		next, err := mergeSettings(s.Settings(), doc.Settings, importKeys(doc, o), o.Rules)
		if err != nil {
			return "", err
		}
		if o.Rules && doc.Platform == backupPlatform && doc.Route < routeOrdered {
			next = migrateRules(next, legacyDirectOnly(doc.Android))
		}
		remapPolicies(&next, policyMap)
		if err := s.SaveSettings(next); err != nil {
			return "", err
		}
		if o.Rules {
			done = append(done, i18n.T("分流规则"))
		}
		if o.Appearance {
			done = append(done, i18n.T("外观"))
		}
		if o.Settings {
			done = append(done, i18n.T("设置"))
		}
		// the app applies the Android preferences of the chosen parts
		if (o.Appearance || o.Settings) && len(doc.Android) > 0 && json.Valid(doc.Android) {
			res.Android = doc.Android
		}
	}
	if len(done) == 0 {
		return "", errors.New(i18n.T("备份里没有选中的内容"))
	}
	s.Flush()
	res.Message = i18n.T("已导入：") + strings.Join(done, i18n.T("、"))
	return marshal(res)
}

// mergeSettings applies the given keys of an imported settings document (all
// of them for keys == nil, except the rules unless withRules). It is the
// desktop app's mergeSettings.
func mergeSettings(cur store.Settings, imported json.RawMessage, keys []string, withRules bool) (store.Settings, error) {
	var in map[string]json.RawMessage
	if err := json.Unmarshal(imported, &in); err != nil {
		return cur, errors.New(i18n.T("备份里的设置已损坏"))
	}
	b, _ := json.Marshal(cur)
	var out map[string]json.RawMessage
	json.Unmarshal(b, &out)
	for k, v := range in {
		if keys != nil && !contains(keys, k) {
			continue
		}
		if keys == nil && !withRules && contains(ruleSettings, k) {
			continue
		}
		out[k] = v
	}
	// levels of a backup made before the v2.1 scales are converted (see
	// store.Settings.ThemeScale)
	_, light := in["lightLevel"]
	_, dark := in["darkLevel"]
	if _, scale := in["themeScale"]; (light || dark) && !scale && (keys == nil || contains(keys, "lightLevel")) {
		out["themeScale"] = json.RawMessage("0")
	}
	if withRules {
		// rules from the backup replace the current ones
		if _, ok := in["rules"]; !ok {
			out["rules"] = json.RawMessage("[]")
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
	fix := func(t string) string {
		id := store.TargetPolicy(t)
		if id == 0 {
			return t
		}
		if n, ok := m[id]; ok {
			return store.PolicyTarget(n)
		}
		return store.TargetProxy
	}
	for i := range s.Rules {
		s.Rules[i].Target = fix(s.Rules[i].Target)
	}
	for i := range s.Scenes {
		s.Scenes[i].Target = fix(s.Scenes[i].Target)
	}
}

func pickKeys(raw []byte, keys []string) ([]byte, error) {
	var m map[string]json.RawMessage
	if err := json.Unmarshal(raw, &m); err != nil {
		return nil, err
	}
	out := map[string]json.RawMessage{}
	for _, k := range keys {
		if v, ok := m[k]; ok {
			out[k] = v
		}
	}
	return json.Marshal(out)
}

func dropKeys(raw []byte, keys []string) ([]byte, error) {
	var m map[string]json.RawMessage
	if err := json.Unmarshal(raw, &m); err != nil {
		return nil, err
	}
	for _, k := range keys {
		delete(m, k)
	}
	return json.Marshal(m)
}

func contains(list []string, s string) bool {
	for _, v := range list {
		if v == s {
			return true
		}
	}
	return false
}

// routeOrdered marks backups whose rules are checked in their stored order.
const routeOrdered = 3

// legacyDirectOnly reports whether the Android preferences of a 1.x backup
// chose "everything direct" (a scope then, a routing mode since 2.0).
func legacyDirectOnly(android json.RawMessage) bool {
	var p struct {
		RouteScope string `json:"routeScope"`
	}
	return json.Unmarshal(android, &p) == nil && p.RouteScope == "direct"
}

// settingsPart names the part of a backup a settings key belongs to:
// "rules", "appearance" or "settings" (the other settings).
func settingsPart(key string) string {
	switch {
	case contains(ruleSettings, key):
		return "rules"
	case contains(appearanceSettings, key):
		return "appearance"
	}
	return "settings"
}

// keepParts keeps the settings keys of the parts o asks for.
func keepParts(raw []byte, o backupOptions) ([]byte, error) {
	var m map[string]json.RawMessage
	if err := json.Unmarshal(raw, &m); err != nil {
		return nil, err
	}
	want := map[string]bool{"rules": o.Rules, "appearance": o.Appearance, "settings": o.Settings}
	for k := range m {
		if !want[settingsPart(k)] {
			delete(m, k)
		}
	}
	return json.Marshal(m)
}

// importKeys lists the settings keys of a backup to apply for the parts o
// asks for. From the other system only what means the same here is taken,
// and the routing mode stays the home page's choice.
func importKeys(doc backupDoc, o backupOptions) []string {
	var in map[string]json.RawMessage
	json.Unmarshal(doc.Settings, &in)
	same := doc.Platform == backupPlatform
	keys := []string{} // not nil: mergeSettings takes nil for "everything"
	for k := range in {
		switch settingsPart(k) {
		case "rules":
			if o.Rules && (same || k != "routeMode") {
				keys = append(keys, k)
			}
		case "appearance":
			if o.Appearance {
				keys = append(keys, k)
			}
		default:
			if o.Settings && (same || contains(portableSettings, k)) {
				keys = append(keys, k)
			}
		}
	}
	return keys
}
