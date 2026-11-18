package nbcore

import (
	"testing"

	"nextbox/internal/store"
)

func TestPhoneSettingsDropsPCOnlyScenes(t *testing.T) {
	set := store.Settings{Scenes: []store.Scene{{ID: "games", Enabled: true}, {ID: "cn", Enabled: true}}}
	got := phoneSettings(set)
	if got.Scenes[0].Enabled || !got.Scenes[1].Enabled {
		t.Fatalf("scenes on the phone: %+v", got.Scenes)
	}
	if !set.Scenes[0].Enabled {
		t.Fatal("the stored settings were changed")
	}
}
