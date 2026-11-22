package nbcore

import (
	"encoding/json"
	"strings"
	"testing"

	"nextbox/internal/config"
	"nextbox/internal/store"
)

// The AI and streaming scenes that were on become rule sets in their place,
// with their target; routing stays the same.
func TestFoldScenes(t *testing.T) {
	s := store.DefaultSettings()
	s.RouteMode = "rule"
	for i := range s.Scenes {
		switch s.Scenes[i].ID {
		case "ai":
			s.Scenes[i].Enabled, s.Scenes[i].Target = true, "direct"
		case "media":
			s.Scenes[i].Enabled = true
		case "games":
			s.Scenes[i].Enabled = true
		}
	}
	s.Normalize()
	if !foldScenes(&s) {
		t.Fatal("nothing folded")
	}
	s.Normalize()
	if foldScenes(&s) {
		t.Fatal("folded twice")
	}
	sets := map[string]store.Rule{}
	pos := map[string]int{}
	for i, r := range s.Rules {
		if r.IsSet() {
			sets[r.Name] = r
			pos[r.Name] = i
		}
		if r.Kind == store.KindScene {
			pos["scene:"+r.Values[0]] = i
		}
	}
	ai, media := sets["AI 服务"], sets["流媒体"]
	if ai.Target != "direct" || len(ai.Items) != 1 || ai.Items[0].Values[0] != "category-ai-!cn" {
		t.Fatalf("ai = %+v", ai)
	}
	if media.Target != store.TargetProxy || len(media.Items[0].Values) != 7 {
		t.Fatalf("media = %+v", media)
	}
	if pos["AI 服务"] != pos["scene:ai"]-1 || pos["流媒体"] != pos["scene:media"]-1 {
		t.Fatalf("not in the scenes' places: %v", pos)
	}
	for _, sc := range s.Scenes {
		if (sc.ID == "ai" || sc.ID == "media") && sc.Enabled {
			t.Fatalf("%s still on", sc.ID)
		}
		if sc.ID == "games" && !sc.Enabled {
			t.Fatal("games is kept for Windows")
		}
	}

	// the phone routes the sites by the new rule sets; game downloads not
	need := strings.Join(config.RequiredRuleSets(phoneSettings(s)), ",")
	for _, want := range []string{"geosite-category-ai-!cn", "geosite-netflix"} {
		if !strings.Contains(need, want) {
			t.Errorf("rule-sets %s lack %s", need, want)
		}
	}
	if strings.Contains(need, "games") {
		t.Errorf("game downloads are routed on the phone: %s", need)
	}
}

// A Windows backup with the AI scene on: the inspection says it becomes a
// rule set, and the import makes it one.
func TestFoldScenesOnImport(t *testing.T) {
	s := openTemp(t)
	info, _ := InspectBackup(windowsBackup, "")
	if !strings.Contains(info, `"scenes":["ai"]`) {
		t.Fatalf("inspect: %s", info)
	}
	if _, err := ImportBackup(windowsBackup, opts(false, true, false, "", false)); err != nil {
		t.Fatal(err)
	}
	var b []byte
	b, _ = json.Marshal(s.Settings().Rules)
	if !strings.Contains(string(b), `"name":"AI 服务"`) {
		t.Fatalf("rules: %s", b)
	}
}
