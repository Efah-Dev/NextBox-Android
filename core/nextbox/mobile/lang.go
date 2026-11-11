package nbcore

import (
	"embed"
	"encoding/json"

	"nextbox/internal/i18n"
)

// Dictionaries: source strings are Simplified Chinese and double as keys.
//
//	en.json, zh-TW.json   shared with the desktop app
//	*-android.json        strings that only the Android app has
//
//go:embed i18n/*.json
var dictFS embed.FS

func init() {
	en := map[string]string{}
	for _, name := range []string{"i18n/en.json", "i18n/en-android.json"} {
		if b, err := dictFS.ReadFile(name); err == nil {
			json.Unmarshal(b, &en)
		}
	}
	type twDict struct {
		Phrases [][2]string `json:"phrases"`
		Chars   struct {
			From string `json:"from"`
			To   string `json:"to"`
		} `json:"chars"`
	}
	var tw twDict
	for _, name := range []string{"i18n/zh-TW.json", "i18n/zh-TW-android.json"} {
		var part twDict
		if b, err := dictFS.ReadFile(name); err == nil && json.Unmarshal(b, &part) == nil {
			tw.Phrases = append(tw.Phrases, part.Phrases...)
			tw.Chars.From += part.Chars.From
			tw.Chars.To += part.Chars.To
		}
	}
	enJSON, _ := json.Marshal(en)
	twJSON, _ := json.Marshal(tw)
	i18n.Init(enJSON, twJSON)
}

// SetLanguage selects "zh-CN", "zh-TW" or "en" for Translate and for the
// messages nbcore returns.
func SetLanguage(lang string) { i18n.SetLang(lang) }

// Translate returns the Simplified Chinese source string s in the current
// language (s itself when there is no translation).
func Translate(s string) string { return i18n.T(s) }
