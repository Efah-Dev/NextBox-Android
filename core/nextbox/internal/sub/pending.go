package sub

import (
	"strings"
	"sync"
	"time"
)

// Adding a subscription found in the clipboard (or dropped, or scanned): its
// name is the one in the link, else the one its server sends (Result.Title);
// only with neither is the user asked. The subscription is downloaded before
// that question, and Pending keeps the download so that adding it then does
// not download it again.

// AutoName is the name a subscription gets without asking: the link's own,
// else its server's; "" when the user has to name it.
func AutoName(linkName string, res *Result) string {
	if n := strings.TrimSpace(linkName); n != "" {
		return n
	}
	if res != nil {
		return strings.TrimSpace(res.Title)
	}
	return ""
}

// Pending is a subscription downloaded while the user names it.
type Pending struct {
	mu  sync.Mutex
	url string
	res *Result
	at  time.Time
}

// pendingFor is how long a kept download stays usable.
const pendingFor = 5 * time.Minute

// Keep holds the download of url until Take.
func (p *Pending) Keep(url string, res *Result) {
	p.mu.Lock()
	defer p.mu.Unlock()
	p.url, p.res, p.at = url, res, time.Now()
}

// Take returns the download kept for url (nil if there is none, it is for
// another address or it is too old) and forgets it.
func (p *Pending) Take(url string) *Result {
	p.mu.Lock()
	defer p.mu.Unlock()
	res, ok := p.res, p.url == url && time.Since(p.at) <= pendingFor
	p.url, p.res = "", nil
	if !ok {
		return nil
	}
	return res
}
