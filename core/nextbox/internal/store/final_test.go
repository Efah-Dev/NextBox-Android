package store

import "testing"

// Deleting a policy group sends the rules and the last step that used it to
// the selected node.
func TestDropPolicy(t *testing.T) {
	s := DefaultSettings()
	s.FinalTarget = PolicyTarget(3)
	s.Rules = []Rule{{Kind: "suffix", Values: []string{"a.com"}, Target: PolicyTarget(3)}}
	if !s.DropPolicy(3) || s.FinalTarget != "" || s.Rules[0].Target != TargetProxy {
		t.Errorf("after DropPolicy: final %q, rule %q", s.FinalTarget, s.Rules[0].Target)
	}
	if s.DropPolicy(3) {
		t.Error("nothing used the group any more")
	}
}
