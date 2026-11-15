package nbtest

import (
	"bytes"
	"context"
	"os"

	"github.com/sagernet/sing-box/adapter"
	"github.com/sagernet/sing-box/common/srs"
	"github.com/sagernet/sing-box/route/rule"
	M "github.com/sagernet/sing/common/metadata"

	nbcore "nextbox/mobile"
)

// The rule tester of nbcore asks sing-box itself whether a rule-set file
// matches a domain or IP, the same way `sing-box rule-set match` does.
func init() { nbcore.SetRuleSetMatcherFunc(matchRuleSet) }

func matchRuleSet(path, host string) (bool, error) {
	content, err := os.ReadFile(path)
	if err != nil {
		return false, err
	}
	compat, err := srs.Read(bytes.NewReader(content), false)
	if err != nil {
		return false, err
	}
	plain, err := compat.Upgrade()
	if err != nil {
		return false, err
	}
	var metadata adapter.InboundContext
	if addr := M.ParseAddr(host); addr.IsValid() {
		metadata.Destination = M.SocksaddrFrom(addr, 0)
	} else {
		metadata.Domain = host
	}
	for _, opts := range plain.Rules {
		r, err := rule.NewHeadlessRule(context.Background(), opts)
		if err != nil {
			return false, err
		}
		if r.Match(&metadata) {
			return true, nil
		}
	}
	return false, nil
}
