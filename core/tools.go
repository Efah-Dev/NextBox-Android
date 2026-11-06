//go:build tools

// Package core pins the Go packages bound into app/libs/libbox.aar:
// sing-box's libbox (the proxy core), NextBox's nbcore (data store, share
// links, subscriptions, config builder) and nbtest (URL latency test). They
// are bound by one gomobile call, because separate AARs would each carry
// their own Go runtime.
package core

import (
	_ "github.com/sagernet/gomobile/bind"
	_ "github.com/sagernet/sing-box/experimental/libbox"
	_ "nextbox-android/core/nbtest"
	_ "nextbox/mobile"
)
