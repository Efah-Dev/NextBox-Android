package sub

import (
	"testing"
	"time"
)

func TestAutoNameAndPending(t *testing.T) {
	if n := AutoName(" 机场 ", &Result{Title: "x"}); n != "机场" {
		t.Errorf("link name: %q", n)
	}
	if n := AutoName("", &Result{Title: "Example"}); n != "Example" {
		t.Errorf("server name: %q", n)
	}
	if n := AutoName("", &Result{}); n != "" {
		t.Errorf("no name: %q", n)
	}

	var p Pending
	res := &Result{Title: "kept"}
	p.Keep("https://a.example/sub", res)
	if p.Take("https://b.example/sub") != nil {
		t.Error("another address took the download")
	}
	p.Keep("https://a.example/sub", res)
	if p.Take("https://a.example/sub") != res {
		t.Error("the download was not kept")
	}
	if p.Take("https://a.example/sub") != nil {
		t.Error("taken twice")
	}
	p.Keep("https://a.example/sub", res)
	p.at = time.Now().Add(-pendingFor - time.Second)
	if p.Take("https://a.example/sub") != nil {
		t.Error("a stale download was used")
	}
}
