package nbcore

import (
	"encoding/base64"
	"encoding/json"
	"net/url"
	"strings"

	"nextbox/internal/link"
)

type classified struct {
	// Kind is "subscription" (URL holds it), "nodes" (Count share links or
	// a subscription's content) or "none".
	Kind  string `json:"kind"`
	URL   string `json:"url,omitempty"`
	Name  string `json:"name,omitempty"`
	Count int    `json:"count,omitempty"`
	// LikelyProxy marks an http(s) address that looks more like an HTTP
	// proxy ("http://user:pass@1.2.3.4:8080") than a subscription; the app
	// still offers both.
	LikelyProxy bool `json:"likelyProxy,omitempty"`
}

// Classify tells what a scanned QR code, the clipboard or a shared text holds:
// a subscription address, nodes, or nothing NextBox can use. Subscription
// addresses are also read from one-click import links
// (clash://install-config?url=…, sing-box://import-remote-profile?url=…,
// sub://<base64>).
func Classify(text string) string {
	text = strings.TrimSpace(strings.TrimPrefix(text, string(rune(0xFEFF))))
	res := classified{Kind: "none"}
	if text == "" {
		b, _ := json.Marshal(res)
		return string(b)
	}
	single := !strings.ContainsAny(text, "\n\r")
	if single {
		if u, name, ok := subscriptionURL(text); ok {
			res.Kind, res.URL, res.Name = "subscription", u, name
			res.LikelyProxy = likelyHTTPProxy(u)
			b, _ := json.Marshal(res)
			return string(b)
		}
	}
	if nodes, _, err := link.ParseContent(text); err == nil && len(nodes) > 0 {
		res.Kind, res.Count = "nodes", len(nodes)
	}
	b, _ := json.Marshal(res)
	return string(b)
}

// subscriptionURL reads a subscription address and an optional name.
func subscriptionURL(s string) (string, string, bool) {
	lower := strings.ToLower(s)
	switch {
	case strings.HasPrefix(lower, "http://") || strings.HasPrefix(lower, "https://"):
		u, err := url.Parse(s)
		if err != nil || u.Host == "" {
			return "", "", false
		}
		return s, nameFromURL(u, ""), true
	case strings.HasPrefix(lower, "clash://") || strings.HasPrefix(lower, "clashmeta://") ||
		strings.HasPrefix(lower, "sing-box://") || strings.HasPrefix(lower, "hiddify://") ||
		strings.HasPrefix(lower, "mihomo://") || strings.HasPrefix(lower, "stash://"):
		u, err := url.Parse(s)
		if err != nil {
			return "", "", false
		}
		target := u.Query().Get("url")
		if !strings.HasPrefix(target, "http://") && !strings.HasPrefix(target, "https://") {
			return "", "", false
		}
		name := u.Query().Get("name")
		if name == "" && u.Fragment != "" {
			name = u.Fragment
		}
		tu, err := url.Parse(target)
		if err != nil || tu.Host == "" {
			return "", "", false
		}
		return target, nameFromURL(tu, name), true
	case strings.HasPrefix(lower, "sub://"):
		// Shadowrocket: sub://base64(url)#name
		body, frag, _ := strings.Cut(s[len("sub://"):], "#")
		for _, enc := range []*base64.Encoding{base64.StdEncoding, base64.RawStdEncoding, base64.URLEncoding, base64.RawURLEncoding} {
			if b, err := enc.DecodeString(body); err == nil {
				target := strings.TrimSpace(string(b))
				if tu, err := url.Parse(target); err == nil && tu.Host != "" && strings.HasPrefix(tu.Scheme, "http") {
					name, _ := url.PathUnescape(frag)
					return target, nameFromURL(tu, name), true
				}
			}
		}
	}
	return "", "", false
}

func nameFromURL(u *url.URL, name string) string {
	if name = strings.TrimSpace(name); name != "" {
		return name
	}
	return strings.TrimPrefix(u.Hostname(), "www.")
}

// likelyHTTPProxy: credentials or an IP address, and nothing after the port.
func likelyHTTPProxy(raw string) bool {
	u, err := url.Parse(raw)
	if err != nil {
		return false
	}
	bare := (u.Path == "" || u.Path == "/") && u.RawQuery == ""
	return bare && (u.User != nil || u.Port() != "")
}
