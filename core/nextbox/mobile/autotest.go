package nbcore

import (
	"sync"

	"nextbox/internal/autotest"
)

// GroupTester asks the running core to test a policy group now (the app
// does it through the libbox command client).
type GroupTester interface {
	TestGroup(tag string)
}

var (
	autoMu     sync.Mutex
	autoGroups []autotest.Group // the automatic policy groups of the config BuildConfig made last
	autoClock  *autotest.Clock
)

// StartAutoTest tests the automatic policy groups of the config built last
// on NextBox's own clock (see autotest): every interval, and after a network
// change. It replaces a clock started before. It returns how many groups it
// tests.
func StartAutoTest(t GroupTester) int32 {
	autoMu.Lock()
	defer autoMu.Unlock()
	autoClock.Stop()
	autoClock = autotest.Start(autoGroups, t.TestGroup)
	return int32(autoClock.Len())
}

// StopAutoTest ends the clock (the core stopped).
func StopAutoTest() {
	autoMu.Lock()
	defer autoMu.Unlock()
	autoClock.Stop()
	autoClock = nil
}

// NetworkChanged tells the clock that the phone moved to another network:
// the groups are tested once it has settled, and their intervals start again.
func NetworkChanged() {
	autoMu.Lock()
	c := autoClock
	autoMu.Unlock()
	c.NetworkChanged()
}
