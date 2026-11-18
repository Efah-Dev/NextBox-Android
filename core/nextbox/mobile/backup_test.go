package nbcore

import (
	"encoding/json"
	"slices"
	"strings"
	"testing"

	"nextbox/internal/store"
	"nextbox/internal/vault"
)

func openTemp(t *testing.T) *store.Store {
	t.Helper()
	mu.Lock()
	st = nil
	mu.Unlock()
	if err := Open(t.TempDir()); err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { mu.Lock(); st = nil; mu.Unlock() })
	s, _ := current()
	return s
}

func opts(nodes, rules, settings bool, password string, replace bool) string {
	// "settings" here also takes the look along, as the one part did before 2.0
	b, _ := json.Marshal(backupOptions{Nodes: nodes, Rules: rules, Appearance: settings, Settings: settings, Password: password, Replace: replace})
	return string(b)
}

// ruleList writes rules as kind:values>target, the places of ads and the
// scenes included.
func ruleList(rules []store.Rule) string {
	var out []string
	for _, r := range rules {
		out = append(out, r.Kind+":"+strings.Join(r.Values, ",")+">"+r.Target)
	}
	return strings.Join(out, " | ")
}

func TestBackupRoundTrip(t *testing.T) {
	openTemp(t)
	gid, _ := AddGroup("g")
	ImportText("ss://YWVzLTEyOC1nY206cGFzcw@1.2.3.4:8388#a\ntrojan://p@5.6.7.8:443#b", gid)

	out, err := ExportBackup(opts(true, true, true, "pw", false), `{"perAppList":["cmb.pb"]}`)
	if err != nil {
		t.Fatal(err)
	}
	if _, ok := vault.Parse([]byte(out)); !ok {
		t.Fatal("expected an encrypted backup")
	}
	info, _ := InspectBackup(out, "")
	if !strings.Contains(info, `"needPassword":true`) {
		t.Fatalf("inspect without password: %s", info)
	}
	info, err = InspectBackup(out, "pw")
	if err != nil || !strings.Contains(info, `"platform":"android"`) || !strings.Contains(info, `"nodes":2`) ||
		!strings.Contains(info, `"hasRules":true`) || !strings.Contains(info, `"hasSettings":true`) {
		t.Fatalf("inspect: %s %v", info, err)
	}
	res, err := ImportBackup(out, opts(true, true, true, "pw", true))
	if err != nil || !strings.Contains(res, "cmb.pb") {
		t.Fatalf("import: %s %v", res, err)
	}
	// the Android preferences only come with the other settings
	res, err = ImportBackup(out, opts(true, true, false, "pw", true))
	if err != nil || strings.Contains(res, "cmb.pb") {
		t.Fatalf("import without settings: %s %v", res, err)
	}
	if _, err := ExportBackup(opts(false, false, false, "", false), "{}"); err == nil {
		t.Fatal("an empty backup should be refused")
	}
	// nothing but the nodes: no settings, no preferences
	out, _ = ExportBackup(opts(true, false, false, "", false), `{"perAppList":["x"]}`)
	var doc backupDoc
	json.Unmarshal([]byte(out), &doc)
	if len(doc.Profiles) == 0 || len(doc.Settings) != 0 || len(doc.Android) != 0 {
		t.Fatalf("nodes only: %s", out)
	}
}

// A backup of NextBox for Windows 2.1: rules in the user's order with the
// places of ad blocking and the scenes, a program rule, the AI scene on.
const windowsBackup = `{"app":"NextBox","version":2,"platform":"windows",
"profiles":{"nextId":3,"groups":[{"id":1,"name":"g"}],"profiles":[{"id":2,"groupId":1,"name":"n","type":"socks","outbound":{"type":"socks","server":"1.2.3.4","server_port":1080}}]},
"settings":{"routeMode":"global","mixedPort":2030,"tunEnabled":false,"theme":"dark","blockAds":true,"themeScale":2,"lightLevel":35,
"rules":[{"kind":"scene","values":["cn"],"target":""},{"kind":"suffix","values":["a.com"],"target":"proxy","app":"youtube"},{"kind":"ads","values":[],"target":""},
{"kind":"process","values":["chrome.exe"],"target":"direct"},{"kind":"scene","values":["ai"],"target":""},{"kind":"suffix","values":["b.com"],"target":"direct"},
{"kind":"scene","values":["media"],"target":""},{"kind":"scene","values":["games"],"target":""}],
"scenes":[{"id":"ai","enabled":true,"target":"proxy"},{"id":"media","enabled":false,"target":"proxy"},{"id":"games","enabled":false,"target":"direct"},{"id":"cn","enabled":true,"target":"direct"}]}}`

func TestWindowsBackup(t *testing.T) {
	s := openTemp(t)
	SetRouteMode("direct")
	info, err := InspectBackup(windowsBackup, "")
	if err != nil || !strings.Contains(info, `"rules":3`) || !strings.Contains(info, `"desktopRules":1`) || !strings.Contains(info, `"scenes":["ai"]`) {
		t.Fatalf("inspect: %s %v", info, err)
	}
	if _, err := ImportBackup(windowsBackup, opts(true, true, true, "", false)); err != nil {
		t.Fatal(err)
	}
	set := s.Settings()
	// this phone's own settings and home page choice stay
	if set.MixedPort != 2080 || !set.TunEnabled || set.Theme != "dark" || set.RouteMode != "direct" || set.LightLevel != 35 {
		t.Fatalf("settings: port %d tun %v theme %s mode %s light %d", set.MixedPort, set.TunEnabled, set.Theme, set.RouteMode, set.LightLevel)
	}
	if !set.Scenes[slices.IndexFunc(set.Scenes, func(sc store.Scene) bool { return sc.ID == "cn" })].Enabled {
		t.Error("the bypass step of the backup is on")
	}
	// the order and places are kept, scenes included (2.0 routes like Windows)
	want := "scene:cn> | suffix:a.com>proxy | ads:> | process:chrome.exe>direct | scene:ai> | suffix:b.com>direct | scene:media> | scene:games>"
	if got := ruleList(set.Rules); got != want {
		t.Fatalf("rules:\n%s\nwant:\n%s", got, want)
	}
	// the program rule is not part of the phone's route
	cfg, err := BuildConfig(t.TempDir(), "")
	if err != nil || strings.Contains(cfg, "chrome.exe") {
		t.Fatalf("config: %v", err)
	}

	// back to the computer: the same rules in the same order
	out, err := ExportBackup(opts(false, true, false, "", false), "{}")
	if err != nil {
		t.Fatal(err)
	}
	var doc backupDoc
	json.Unmarshal([]byte(out), &doc)
	var back store.Settings
	json.Unmarshal(doc.Settings, &back)
	if got := ruleList(back.Rules); got != want {
		t.Fatalf("exported rules:\n%s", got)
	}
	if !slices.ContainsFunc(back.Rules, func(r store.Rule) bool { return r.App == "youtube" }) {
		t.Fatal("the catalog service of a desktop rule is lost")
	}
	var keys map[string]json.RawMessage
	json.Unmarshal(doc.Settings, &keys)
	if _, ok := keys["mixedPort"]; ok || len(doc.Profiles) != 0 {
		t.Fatalf("rules only: %s", out)
	}
}

// A backup of only the other settings leaves the rules alone.
func TestPartialBackup(t *testing.T) {
	s := openTemp(t)
	SaveRules(`[{"kind":"suffix","values":["keep.com"],"target":"direct"}]`, "")
	set := s.Settings()
	set.BlockAds = true
	s.SaveSettings(set)
	only := `{"app":"NextBox","version":2,"platform":"windows","settings":{"theme":"light","remoteDns":"https://9.9.9.9/dns-query","mixedPort":1}}`
	info, _ := InspectBackup(only, "")
	if !strings.Contains(info, `"hasRules":false`) || !strings.Contains(info, `"hasSettings":true`) {
		t.Fatalf("inspect: %s", info)
	}
	if _, err := ImportBackup(only, opts(true, true, true, "", true)); err != nil {
		t.Fatal(err)
	}
	set = s.Settings()
	if !set.BlockAds || !strings.Contains(ruleList(set.Rules), "keep.com") || set.RemoteDNS != "https://9.9.9.9/dns-query" || set.MixedPort == 1 {
		t.Fatalf("after a settings-only backup: ads %v rules %s dns %s port %d", set.BlockAds, ruleList(set.Rules), set.RemoteDNS, set.MixedPort)
	}
}

// The rules page saves the rules in the order it shows them (2.0: like
// Windows, the user decides the order); the places of ad blocking and the
// scenes are kept, and a list without them gets them (store.placeSteps).
func TestSaveRulesOrder(t *testing.T) {
	s := openTemp(t)
	if _, err := ImportBackup(windowsBackup, opts(false, true, false, "", false)); err != nil {
		t.Fatal(err)
	}
	var rules []store.Rule
	json.Unmarshal(mustJSON(s.Settings().Rules), &rules)
	rules = append([]store.Rule{{Kind: "domain", Values: []string{"x.com"}, Target: store.TargetBlock}}, rules...)
	if err := SaveRules(string(mustJSON(rules)), ""); err != nil {
		t.Fatal(err)
	}
	want := "domain:x.com>block | scene:cn> | suffix:a.com>proxy | ads:> | process:chrome.exe>direct | scene:ai> | suffix:b.com>direct | scene:media> | scene:games>"
	if got := ruleList(s.Settings().Rules); got != want {
		t.Fatalf("rules:\n%s\nwant:\n%s", got, want)
	}

	s2 := openTemp(t)
	SaveRules(`[{"kind":"suffix","values":["p.com"],"target":"proxy"},{"kind":"suffix","values":["d.com"],"target":"direct"}]`, `[{"id":"cn","enabled":true,"target":"direct"}]`)
	want = "ads:> | suffix:p.com>proxy | suffix:d.com>direct | scene:ai> | scene:media> | scene:games> | scene:cn>"
	if got := ruleList(s2.Settings().Rules); got != want {
		t.Fatalf("fresh phone:\n%s\nwant:\n%s", got, want)
	}
	if !s2.Settings().Scenes[0].Enabled || s2.Settings().Scenes[0].ID != "cn" {
		t.Fatalf("scenes: %+v", s2.Settings().Scenes)
	}
}

func mustJSON(v any) []byte {
	b, _ := json.Marshal(v)
	return b
}
