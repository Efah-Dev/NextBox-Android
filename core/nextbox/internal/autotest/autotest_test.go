package autotest

import (
	"sync"
	"testing"
	"time"
)

type recorder struct {
	mu   sync.Mutex
	at   map[string][]time.Duration
	from time.Time
}

func newRecorder() *recorder { return &recorder{at: map[string][]time.Duration{}, from: time.Now()} }

func (r *recorder) test(tag string) {
	r.mu.Lock()
	r.at[tag] = append(r.at[tag], time.Since(r.from))
	r.mu.Unlock()
}

func (r *recorder) count(tag string) int {
	r.mu.Lock()
	defer r.mu.Unlock()
	return len(r.at[tag])
}

func TestInterval(t *testing.T) {
	r := newRecorder()
	c := Start([]Group{{Tag: "a", Every: 40 * time.Millisecond}, {Tag: "", Every: time.Millisecond}}, r.test)
	defer c.Stop()
	if c.Len() != 1 {
		t.Fatalf("groups = %d", c.Len())
	}
	time.Sleep(190 * time.Millisecond)
	if n := r.count("a"); n < 3 || n > 5 {
		t.Errorf("tests in 190 ms at 40 ms: %d", n)
	}
}

// A network change tests at once (after settling) and restarts the interval.
func TestNetworkChangeRestartsInterval(t *testing.T) {
	Settle = 20 * time.Millisecond
	r := newRecorder()
	c := Start([]Group{{Tag: "a", Every: 200 * time.Millisecond}}, r.test)
	defer c.Stop()
	time.Sleep(150 * time.Millisecond)
	// three changes in a row are one test
	c.NetworkChanged()
	time.Sleep(5 * time.Millisecond)
	c.NetworkChanged()
	c.NetworkChanged()
	time.Sleep(60 * time.Millisecond) // ~210 ms: tested by the change, not the old interval
	if n := r.count("a"); n != 1 {
		t.Fatalf("tests after the change: %d", n)
	}
	r.mu.Lock()
	first := r.at["a"][0]
	r.mu.Unlock()
	if first < 170*time.Millisecond {
		t.Errorf("tested at %v, before the change settled", first)
	}
	// the old interval (due at 200 ms) did not fire; the new one comes 200 ms after the change
	time.Sleep(100 * time.Millisecond) // ~310 ms
	if n := r.count("a"); n != 1 {
		t.Errorf("tests before the new interval: %d", n)
	}
	time.Sleep(130 * time.Millisecond) // ~440 ms
	if n := r.count("a"); n != 2 {
		t.Errorf("tests after the new interval: %d", n)
	}
}

func TestStop(t *testing.T) {
	Settle = 10 * time.Millisecond
	r := newRecorder()
	c := Start([]Group{{Tag: "a", Every: 30 * time.Millisecond}}, r.test)
	c.NetworkChanged()
	c.Stop()
	time.Sleep(80 * time.Millisecond)
	if n := r.count("a"); n != 0 {
		t.Errorf("tests after Stop: %d", n)
	}
	var nilClock *Clock
	nilClock.NetworkChanged()
	nilClock.Stop()
}
