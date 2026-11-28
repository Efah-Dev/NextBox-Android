// Package autotest runs the speed tests of the automatic policy groups
// (分组代理, "自动用最快的") on NextBox's own clock instead of the core's.
//
// The core tests an urltest group every interval and, when the network
// changes, once more right away, but it keeps its interval running: a group
// tested 10 minutes apart could be tested again a minute after a network
// change. Its clock cannot be reset from outside, so config.Build gives the
// groups an interval that never comes (CoreInterval) and NextBox asks for
// each test: every interval, and when the network changes (after Settle,
// for the new network to work), after which the interval starts again. The
// core still picks the fastest member after a test (with its tolerance).
package autotest

import (
	"sync"
	"time"
)

// CoreInterval is the urltest interval written into the config: the core's
// own clock, which NextBox replaces.
const CoreInterval = 24 * time.Hour

// Settle is how long a network change waits before the groups are tested:
// several changes in a row (Wi-Fi going away, cellular coming up) are one.
var Settle = 2 * time.Second

// Group is an automatic policy group in the running core.
type Group struct {
	Tag   string        // its outbound
	Every time.Duration // its test interval
}

// Clock tests the groups it was started with until Stop.
type Clock struct {
	test func(tag string)

	mu      sync.Mutex
	groups  []*group
	settle  *time.Timer
	stopped bool
}

type group struct {
	Group
	timer *time.Timer
}

// Start begins the intervals of the groups; test asks the core to test one
// group (it may block until the test is done). A clock without groups does
// nothing.
func Start(groups []Group, test func(tag string)) *Clock {
	c := &Clock{test: test}
	for _, g := range groups {
		if g.Tag == "" || g.Every <= 0 {
			continue
		}
		e := &group{Group: g}
		e.timer = time.AfterFunc(g.Every, func() { c.fire(e) })
		c.groups = append(c.groups, e)
	}
	return c
}

// fire tests a group whose interval has passed and starts the next one.
func (c *Clock) fire(e *group) {
	c.mu.Lock()
	if c.stopped {
		c.mu.Unlock()
		return
	}
	e.timer.Reset(e.Every)
	c.mu.Unlock()
	c.test(e.Tag)
}

// NetworkChanged tests every group once the network has settled and starts
// their intervals again from then.
func (c *Clock) NetworkChanged() {
	if c == nil {
		return
	}
	c.mu.Lock()
	defer c.mu.Unlock()
	if c.stopped || len(c.groups) == 0 {
		return
	}
	if c.settle != nil {
		c.settle.Reset(Settle)
		return
	}
	c.settle = time.AfterFunc(Settle, c.retest)
}

func (c *Clock) retest() {
	c.mu.Lock()
	if c.stopped {
		c.mu.Unlock()
		return
	}
	c.settle = nil
	var tags []string
	for _, e := range c.groups {
		e.timer.Reset(e.Every)
		tags = append(tags, e.Tag)
	}
	c.mu.Unlock()
	for _, tag := range tags {
		go c.test(tag)
	}
}

// Stop ends the clock; tests already asked for are not waited for.
func (c *Clock) Stop() {
	if c == nil {
		return
	}
	c.mu.Lock()
	defer c.mu.Unlock()
	c.stopped = true
	for _, e := range c.groups {
		e.timer.Stop()
	}
	if c.settle != nil {
		c.settle.Stop()
	}
}

// Len is the number of groups the clock tests.
func (c *Clock) Len() int {
	if c == nil {
		return 0
	}
	return len(c.groups)
}
