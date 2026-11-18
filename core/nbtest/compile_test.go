package nbtest

import (
	"os"
	"path/filepath"
	"testing"
)

// A source rule-set compiles into a binary one that matches what it lists.
func TestCompileRuleSet(t *testing.T) {
	dir := t.TempDir()
	src, dest := filepath.Join(dir, "a.json"), filepath.Join(dir, "a.srs")
	os.WriteFile(src, []byte(`{"version":3,"rules":[{"domain_suffix":["example.com"]},{"ip_cidr":["10.9.0.0/16"]}]}`), 0o644)
	if err := compileRuleSet(src, dest); err != nil {
		t.Fatal(err)
	}
	for host, want := range map[string]bool{"www.example.com": true, "10.9.1.2": true, "example.org": false} {
		if ok, err := matchRuleSet(dest, host); err != nil || ok != want {
			t.Errorf("%s: %v %v", host, ok, err)
		}
	}
	os.WriteFile(src, []byte(`payload:\n  - DOMAIN-SUFFIX,example.com`), 0o644)
	if compileRuleSet(src, dest) == nil {
		t.Error("a Clash rule provider was accepted")
	}
}
