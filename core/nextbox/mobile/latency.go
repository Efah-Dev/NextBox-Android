package nbcore

import (
	"context"
	"sync"
	"time"

	"nextbox/internal/core"
)

// LatencyHandler receives the progress of a latency test.
type LatencyHandler interface {
	OnStart(id int64)
	// OnResult reports a node's latency in ms, or -1 with a message.
	OnResult(id int64, latency int32, message string)
}

var (
	testMu     sync.Mutex
	testCancel context.CancelFunc
	testRun    int
)

// TCPPing measures the TCP handshake time to each node server (ids is a comma
// separated list). It blocks until every node is done or CancelTest is called;
// results are saved to the store.
func TCPPing(ids string, h LatencyHandler) error {
	s, err := current()
	if err != nil {
		return err
	}
	ctx, cancel := context.WithCancel(context.Background())
	testMu.Lock()
	if testCancel != nil {
		testCancel()
	}
	testCancel = cancel
	testRun++
	run := testRun
	testMu.Unlock()
	defer func() {
		testMu.Lock()
		cancel()
		if testRun == run {
			testCancel = nil
		}
		testMu.Unlock()
	}()

	nodes := s.ProfilesByIDs(parseIDs(ids))
	core.TCPPing(ctx, s.Settings(), nodes, core.Progress{
		Start: func(id int64) { h.OnStart(id) },
		Result: func(r core.LatencyResult) {
			s.SetLatency(r.ID, r.Latency, r.Error, time.Now().Unix())
			h.OnResult(r.ID, int32(r.Latency), r.Error)
		},
	})
	return s.Flush()
}

// SetLatency records a latency result (ms, or -1 with a message); call Flush
// afterwards to persist it.
func SetLatency(id int64, latency int32, message string) {
	if s, err := current(); err == nil {
		s.SetLatency(id, int(latency), message, time.Now().Unix())
	}
}

// CancelTest stops a running latency test.
func CancelTest() {
	testMu.Lock()
	defer testMu.Unlock()
	if testCancel != nil {
		testCancel()
	}
}
