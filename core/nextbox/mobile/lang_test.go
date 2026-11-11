package nbcore

import (
	"encoding/json"
	"regexp"
	"strings"
	"testing"
)

var verb = regexp.MustCompile(`%[sd]`)

// Every Android translation must parse, and must keep the format verbs of
// its source string in the same order (they are passed to String.format).
func TestAndroidDictionary(t *testing.T) {
	b, err := dictFS.ReadFile("i18n/en-android.json")
	if err != nil {
		t.Fatal(err)
	}
	var en map[string]string
	if err := json.Unmarshal(b, &en); err != nil {
		t.Fatalf("en-android.json: %v", err)
	}
	if len(en) < 100 {
		t.Fatalf("only %d entries", len(en))
	}
	for k, v := range en {
		if strings.Join(verb.FindAllString(k, -1), "") != strings.Join(verb.FindAllString(v, -1), "") {
			t.Errorf("format verbs differ: %q -> %q", k, v)
		}
	}
}

func TestTranslate(t *testing.T) {
	defer SetLanguage("zh-CN")
	SetLanguage("en")
	if got := Translate("绕过中国大陆"); got != "Bypass mainland China" {
		t.Errorf("en: %q", got)
	}
	SetLanguage("zh-TW")
	if got := Translate("已选 %d 个应用"); got != "已選 %d 個應用" {
		t.Errorf("zh-TW: %q", got)
	}
	if got := Translate("日志"); got != "日誌" {
		t.Errorf("zh-TW phrase: %q", got)
	}
	SetLanguage("zh-CN")
	if got := Translate("绕过中国大陆"); got != "绕过中国大陆" {
		t.Errorf("zh-CN: %q", got)
	}
}
