package nbtest

import (
	"context"
	"net"
	"sync/atomic"

	"github.com/sagernet/sing-box/adapter"
	"github.com/sagernet/sing-box/experimental/libbox"
	tun "github.com/sagernet/sing-tun"
	"github.com/sagernet/sing/common/bufio"
	N "github.com/sagernet/sing/common/network"
)

// proxyTag is the outbound tag nbcore gives the selected node.
const proxyTag = "proxy"

// proxyTracker counts the bytes of connections routed to the proxy outbound,
// so per-node usage leaves out traffic that went direct.
type proxyTracker struct {
	up, down atomic.Int64
}

var tracker proxyTracker

func (t *proxyTracker) RoutedConnection(ctx context.Context, conn net.Conn, metadata adapter.InboundContext, matchedRule adapter.Rule, matchOutbound adapter.Outbound) net.Conn {
	if matchOutbound == nil || matchOutbound.Tag() != proxyTag {
		return conn
	}
	return bufio.NewInt64CounterConn(conn, []*atomic.Int64{&t.up}, []*atomic.Int64{&t.down})
}

func (t *proxyTracker) RoutedPacketConnection(ctx context.Context, conn N.PacketConn, metadata adapter.InboundContext, matchedRule adapter.Rule, matchOutbound adapter.Outbound) N.PacketConn {
	if matchOutbound == nil || matchOutbound.Tag() != proxyTag {
		return conn
	}
	return bufio.NewInt64CounterPacketConn(conn, []*atomic.Int64{&t.up}, nil, []*atomic.Int64{&t.down}, nil)
}

func (t *proxyTracker) RoutedFlow(ctx context.Context, metadata adapter.InboundContext, matchedRule adapter.Rule, matchOutbound adapter.Outbound) tun.FlowTracker {
	return nil
}

// TrackProxy attaches the proxy traffic counter to the box instance the
// command server currently runs. Call it after every start or reload: each
// one creates a new router.
func TrackProxy(server *libbox.CommandServer) {
	if server == nil || server.StartedService == nil {
		return
	}
	instance := server.StartedService.Instance()
	if instance == nil || instance.Box() == nil {
		return
	}
	instance.Box().Router().AppendTracker(&tracker)
}

// ProxyUplink returns the bytes sent through the proxy outbound since the
// process started (cumulative across reloads).
func ProxyUplink() int64 { return tracker.up.Load() }

// ProxyDownlink returns the bytes received through the proxy outbound.
func ProxyDownlink() int64 { return tracker.down.Load() }
