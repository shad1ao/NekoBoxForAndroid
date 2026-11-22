package libcore

import (
	"context"
	"io"
	"net"
	"testing"

	"github.com/sagernet/sing-box/adapter"
	"github.com/sagernet/sing-box/boxapi"
	"github.com/sagernet/sing-box/option"
	"github.com/sagernet/sing/common/bufio"
	M "github.com/sagernet/sing/common/metadata"
	N "github.com/sagernet/sing/common/network"
)

// tagOutbound is only enough for the stats tracker, which reads the outbound tag.
type tagOutbound struct {
	tag string
}

func (o tagOutbound) Type() string           { return "direct" }
func (o tagOutbound) Tag() string            { return o.tag }
func (o tagOutbound) Network() []string      { return []string{N.NetworkTCP, N.NetworkUDP} }
func (o tagOutbound) Dependencies() []string { return nil }
func (o tagOutbound) DialContext(context.Context, string, M.Socksaddr) (net.Conn, error) {
	return nil, io.EOF
}
func (o tagOutbound) ListenPacket(context.Context, M.Socksaddr) (net.PacketConn, error) {
	return nil, io.EOF
}

func TestTrafficStatsCountWithoutClashAPI(t *testing.T) {
	server := boxapi.NewSbV2rayServer(option.V2RayStatsServiceOptions{
		Enabled:   true,
		Outbounds: []string{"proxy"},
	})
	client, remote := net.Pipe()
	defer remote.Close()
	tracked := server.StatsService().RoutedConnection(
		context.Background(), client, adapter.InboundContext{}, nil, tagOutbound{tag: "proxy"},
	)
	payload := []byte("hello traffic")
	go func() {
		_, _ = remote.Write(payload)
		_ = remote.Close()
	}()
	// Same unwrap the connection manager uses before copying. Clash API is not
	// in this chain; the app tracker has to count on its own.
	reader, readCounters := N.UnwrapCountReader(tracked, nil)
	if len(readCounters) == 0 {
		t.Fatal("traffic counters were dropped without the clash API tracker")
	}
	if _, err := bufio.CopyWithCounters(io.Discard, reader, tracked, readCounters, nil, 0, 1); err != nil {
		t.Fatal(err)
	}
	uplink := server.QueryStats("outbound>>>proxy>>>traffic>>>uplink")
	if uplink != int64(len(payload)) {
		t.Fatalf("uplink = %d, want %d", uplink, len(payload))
	}
	if other := server.QueryStats("outbound>>>bypass>>>traffic>>>uplink"); other != 0 {
		t.Fatalf("bypass uplink = %d", other)
	}
}
