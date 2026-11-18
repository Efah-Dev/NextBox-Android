package nbcore

import (
	"strings"
	"testing"
)

// The look is a part of its own: alone it carries the theme and the Android
// look preferences, and the other settings alone leave the look as it is.
func TestAppearanceBackup(t *testing.T) {
	s := openTemp(t)
	SaveSettings(`{"theme":"dark","accentColor":"#123456","recentColors":["#123456"],"directDns":"119.29.29.29"}`)
	out, err := ExportBackup(`{"appearance":true}`, `{"glass":false}`)
	if err != nil {
		t.Fatal(err)
	}
	if strings.Contains(out, "directDns") || !strings.Contains(out, `"glass": false`) {
		t.Fatalf("appearance backup: %s", out)
	}
	info, _ := InspectBackup(out, "")
	if !strings.Contains(info, `"hasAppearance":true`) || strings.Contains(info, `"hasSettings":true`) {
		t.Fatalf("inspect: %s", info)
	}
	SaveSettings(`{"theme":"light","accentColor":"#FFFFFF","recentColors":[]}`)
	res, err := ImportBackup(out, `{"appearance":true}`)
	if err != nil || !strings.Contains(res, `"glass":false`) {
		t.Fatalf("import: %s %v", res, err)
	}
	if set := s.Settings(); set.Theme != "dark" || set.AccentColor != "#123456" || len(set.RecentColors) != 1 {
		t.Fatalf("look not restored: %s %s %v", set.Theme, set.AccentColor, set.RecentColors)
	}

	// a full backup, imported with the other settings only
	full, _ := ExportBackup(`{"appearance":true,"settings":true}`, `{}`)
	SaveSettings(`{"theme":"light","directDns":"223.5.5.5"}`)
	if _, err := ImportBackup(full, `{"settings":true}`); err != nil {
		t.Fatal(err)
	}
	if set := s.Settings(); set.Theme != "light" || set.DirectDNS != "119.29.29.29" {
		t.Fatalf("other settings: theme %s dns %s", set.Theme, set.DirectDNS)
	}
}
