package libcore

import (
	"encoding/base64"
	"encoding/json"
	"strings"
	"testing"

	mierupb "github.com/enfein/mieru/v3/pkg/appctl/appctlpb"
	"github.com/sagernet/sing-box/constant"
	"github.com/sagernet/sing-box/option"
	"google.golang.org/protobuf/proto"
)

func TestEmbeddedCoreIsMbox(t *testing.T) {
	if constant.Version != "1.12.23-m.3.28.0" {
		t.Fatalf("core version = %q, want mbox v1.12.23-m.3.28.0", constant.Version)
	}
	if _, ok := nekoboxAndroidOutboundRegistry().CreateOptions(constant.TypeMieru); !ok {
		t.Fatal("mieru outbound is not registered on the embedded core")
	}
	if _, ok := nekoboxAndroidInboundRegistry().CreateOptions(constant.TypeMieru); !ok {
		t.Fatal("mieru inbound is not registered on the embedded core")
	}
}

func TestMieruOutboundOptionsKeepShareFields(t *testing.T) {
	raw := []byte(`{
		"server":"1.2.3.4",
		"server_port":6666,
		"server_ports":["2000-2002"],
		"transport":"UDP",
		"username":"baozi",
		"password":"manlianpenfen",
		"multiplexing":"MULTIPLEXING_HIGH",
		"traffic_pattern":"QQ==",
		"mtu":1400,
		"handshake_mode":"HANDSHAKE_NO_WAIT"
	}`)
	var options option.MieruOutboundOptions
	if err := json.Unmarshal(raw, &options); err != nil {
		t.Fatal(err)
	}
	if options.Server != "1.2.3.4" || options.ServerPort != 6666 || options.Transport != "UDP" {
		t.Fatalf("server fields = %+v", options)
	}
	if options.UserName != "baozi" || options.Password != "manlianpenfen" {
		t.Fatal("credentials were not decoded")
	}
	if options.MTU != 1400 || options.HandshakeMode != "HANDSHAKE_NO_WAIT" {
		t.Fatalf("share fields were dropped: mtu=%d handshake=%s", options.MTU, options.HandshakeMode)
	}
	if len(options.ServerPortRanges) != 1 || options.ServerPortRanges[0] != "2000-2002" {
		t.Fatalf("server_ports = %#v", options.ServerPortRanges)
	}
}

func TestStandardMieruLinkFixtureMatchesProtobuf(t *testing.T) {
	// Same fixtures as app/src/test/.../MieruFmtTest.kt. Decoding them with
	// mieru's own generated messages checks the hand-written protobuf reader.
	cases := []struct {
		link     string
		profile  string
		user     string
		password string
		address  string
		domain   bool
		port     int32
		portRange string
		protocol mierupb.TransportProtocol
		mtu      int32
		mux      mierupb.MultiplexingLevel
		hand     mierupb.HandshakeMode
	}{
		{
			link:     "mieru://CjwKB2RlZmF1bHQSFgoFYmFvemkSDW1hbmxpYW5wZW5mZW4aEAoHMS4yLjMuNBoFCIo0EAIg+AoqAggEMAISB2RlZmF1bHQ=",
			profile:  "default",
			user:     "baozi",
			password: "manlianpenfen",
			address:  "1.2.3.4",
			port:     6666,
			protocol: mierupb.TransportProtocol_TCP,
			mtu:      1400,
			mux:      mierupb.MultiplexingLevel_MULTIPLEXING_HIGH,
			hand:     mierupb.HandshakeMode_HANDSHAKE_NO_WAIT,
		},
		{
			link:      "mieru://CjUKBGhvbWUSDwoGdSBuYW1lEgVwOmFAYhocEgtleGFtcGxlLmNvbRoNGgkyMDAwLTIwMDIQAQ==",
			profile:   "home",
			user:      "u name",
			password:  "p:a@b",
			address:   "example.com",
			domain:    true,
			portRange: "2000-2002",
			protocol:  mierupb.TransportProtocol_UDP,
		},
	}
	for _, tc := range cases {
		payload := strings.TrimPrefix(tc.link, "mieru://")
		raw, err := base64.StdEncoding.DecodeString(payload)
		if err != nil {
			t.Fatal(err)
		}
		var config mierupb.ClientConfig
		if err := proto.Unmarshal(raw, &config); err != nil {
			t.Fatal(err)
		}
		if len(config.Profiles) != 1 {
			t.Fatalf("profiles = %d", len(config.Profiles))
		}
		profile := config.Profiles[0]
		if profile.GetProfileName() != tc.profile || profile.GetUser().GetName() != tc.user || profile.GetUser().GetPassword() != tc.password {
			t.Fatalf("profile identity = %+v", profile)
		}
		if tc.mtu != 0 && profile.GetMtu() != tc.mtu {
			t.Fatalf("mtu = %d", profile.GetMtu())
		}
		if tc.mux != 0 && profile.GetMultiplexing().GetLevel() != tc.mux {
			t.Fatalf("mux = %s", profile.GetMultiplexing().GetLevel())
		}
		if tc.hand != 0 && profile.GetHandshakeMode() != tc.hand {
			t.Fatalf("handshake = %s", profile.GetHandshakeMode())
		}
		server := profile.Servers[0]
		if tc.domain {
			if server.GetDomainName() != tc.address {
				t.Fatalf("domain = %s", server.GetDomainName())
			}
		} else if server.GetIpAddress() != tc.address {
			t.Fatalf("ip = %s", server.GetIpAddress())
		}
		binding := server.PortBindings[0]
		if binding.GetProtocol() != tc.protocol {
			t.Fatalf("protocol = %s", binding.GetProtocol())
		}
		if tc.port != 0 && binding.GetPort() != tc.port {
			t.Fatalf("port = %d", binding.GetPort())
		}
		if tc.portRange != "" && binding.GetPortRange() != tc.portRange {
			t.Fatalf("port range = %s", binding.GetPortRange())
		}
	}
}
