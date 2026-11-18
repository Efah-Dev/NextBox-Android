package nbtest

import (
	"bytes"
	"os"

	"github.com/sagernet/sing-box/common/srs"
	"github.com/sagernet/sing-box/option"
	"github.com/sagernet/sing/common/json"

	nbcore "nextbox/mobile"
)

// A rule set's rule-set from an address may be in source form (JSON);
// sing-box only loads binary rule-sets, so it is compiled the way
// `sing-box rule-set compile` does.
func init() { nbcore.SetRuleSetCompilerFunc(compileRuleSet) }

func compileRuleSet(src, dest string) error {
	content, err := os.ReadFile(src)
	if err != nil {
		return err
	}
	plain, err := json.UnmarshalExtended[option.PlainRuleSetCompat](content)
	if err != nil {
		return err
	}
	var buf bytes.Buffer
	if err := srs.Write(&buf, plain.Options, plain.Version); err != nil {
		return err
	}
	tmp := dest + ".tmp"
	if err := os.WriteFile(tmp, buf.Bytes(), 0o644); err != nil {
		return err
	}
	return os.Rename(tmp, dest)
}
