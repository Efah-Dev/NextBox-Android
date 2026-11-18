package nbcore

import (
	"strings"
	"testing"
)

// A backup of Android 1.x: the rules in their stored order, which that
// version did not route by (it checked a fixed path), and "everything
// direct" as a scope of its preferences.
const android1xBackup = `{"app":"NextBox","version":2,"platform":"android",
"settings":{"routeMode":"rule","rules":[{"kind":"suffix","values":["example.com"],"target":"direct"},{"kind":"suffix","values":["api.example.com"],"target":"proxy"},{"kind":"keyword","values":["ads"],"target":"block"}]},
"android":{"routeScope":"direct"}}`

func TestImportAndroid1xBackup(t *testing.T) {
	s := openTemp(t)
	if _, err := ImportBackup(android1xBackup, opts(false, true, true, "", false)); err != nil {
		t.Fatal(err)
	}
	set := s.Settings()
	want := "ads:> | keyword:ads>block | suffix:api.example.com>proxy | suffix:example.com>direct | scene:ai> | scene:media> | scene:games> | scene:cn>"
	if got := ruleList(set.Rules); got != want {
		t.Fatalf("rules:\n%s\nwant:\n%s", got, want)
	}
	if set.RouteMode != "direct" {
		t.Fatalf("1.x everything direct became mode %q", set.RouteMode)
	}
}

func TestAndroid2BackupKeepsOrder(t *testing.T) {
	s := openTemp(t)
	SaveRules(`[{"kind":"suffix","values":["example.com"],"target":"direct"},{"kind":"suffix","values":["api.example.com"],"target":"proxy"}]`, "")
	before := ruleList(s.Settings().Rules)
	out, err := ExportBackup(opts(false, true, false, "", false), "")
	if err != nil || !strings.Contains(out, `"route": 3`) {
		t.Fatalf("export: %v\n%s", err, out)
	}
	SaveRules(`[]`, "")
	if _, err := ImportBackup(out, opts(false, true, false, "", false)); err != nil {
		t.Fatal(err)
	}
	if got := ruleList(s.Settings().Rules); got != before {
		t.Fatalf("rules:\n%s\nwant:\n%s", got, before)
	}
}
