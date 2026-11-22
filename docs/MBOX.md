# Embedded mbox core

NekoBox's `libcore` module builds [enfein/mbox](https://github.com/enfein/mbox) and binds it with MatsuriDayo's gomobile fork. mbox is the runtime. The previous MatsuriDayo sing-box checkout is not cloned and is not linked.

## Pin

| | |
| --- | --- |
| Repository | https://github.com/enfein/mbox |
| Tag | `v1.12.23-m.3.28.0` |
| Commit | `2ced282af6a415fbd4b6cb62aa764e79a2450141` |
| Module path | `github.com/sagernet/sing-box` (unchanged, so existing libcore imports keep compiling) |
| Reported version | `1.12.23-m.3.28.0` (`constant.Version`, also passed with `-X` when gomobile binds) |
| mieru library | `github.com/enfein/mieru/v3` `v3.28.0`, as required by that mbox tag |
| libneko | https://github.com/MatsuriDayo/libneko `1c47a3af71990a7b2192e03292b4d246c308ef0b` (logging and VPN protect only) |

`buildScript/lib/core/get_source.sh` clones that tag and applies `buildScript/lib/core/mbox-compat/neko-compat.patch`. The patch is checked with `git apply --check` before it is applied. A dirty or drifted tree fails the fetch instead of silently building something else.

The patch keeps the hooks this app already calls, and forwards two mieru profile fields that mbox 1.12.23 parsed nowhere:

- `nekoutils` selector callback and headless `geoip:` / `geosite:` rules
- `boxapi` HTTP client used by the app's connection tests
- `dialer.DoNotSelectInterface`, used by the Android platform dialer
- do not start the cache file or Clash API only because `PlatformLogWriter` is set
- clear VLESS flow when sing-box multiplex is enabled
- gVisor TUN close workaround (sing-tun 0.7.11 still wraps the dispatcher the same way)
- `mtu` and `handshake_mode` on the mieru outbound, validated as 1280–1400 and the official handshake enum, then copied into the mieru client profile

`libcore/box_include.go` registers mieru inbound and outbound. Mieru profiles are native outbounds. The app no longer downloads or launches `mieru-plugin`.

## Share links

Formats below are the ones published by mieru. This app does not define its own scheme.

- Simple link, `mieru export config simple`: https://github.com/enfein/mieru/blob/v3.28.0/pkg/appctl/url.go and the current file at https://github.com/enfein/mieru/blob/main/pkg/appctl/url.go
- Standard link, `mieru export config`: same files. Bytes after `mieru://` are standard base64 of `proto.Marshal(ClientConfig)`.
- Description: https://github.com/enfein/mieru/blob/main/docs/client-install.md#sharing-client-settings
- Earlier discussion of the simple URL: https://github.com/enfein/mieru/discussions/60
- mihomo import, one proxy per port binding, fragment used as the node name: https://github.com/MetaCubeX/mihomo/pull/2657 and https://github.com/MetaCubeX/mihomo/blob/Alpha/adapter/outbound/mieru.go

mieru and mbox are GPLv3, the same license as this app. The Android parser is a small reader of the documented URL and protobuf layout. It does not vendor mieru's generated code.

### Supported import

Clipboard, QR, the share intent, and subscription text all go through `parseProxies`. A line is accepted when it starts with `mierus://` or `mieru://`.

- `mierus://username:password@host?profile=&port=&protocol=`
  - `port` and `protocol` repeat together. `protocol` is exactly `TCP` or `UDP`.
  - `port` is `1`–`65535` or `begin-end` with `begin <= end`.
  - One NekoBox profile per port binding. A link with several bindings is imported as several profiles, named `profile:port/protocol` (or `fragment:port/protocol` when a fragment is present). mihomo does the same split because one outbound has one transport.
  - `profile` is required once. `mtu`, `multiplexing`, `handshake-mode`, and `traffic-pattern` are optional and may appear once.
  - Multiplexing: `MULTIPLEXING_DEFAULT`, `MULTIPLEXING_OFF`, `MULTIPLEXING_LOW`, `MULTIPLEXING_MIDDLE`, `MULTIPLEXING_HIGH`.
  - Handshake: `HANDSHAKE_DEFAULT`, `HANDSHAKE_STANDARD`, `HANDSHAKE_NO_WAIT`.
  - `mtu`, when present, must be an integer from 1280 to 1400.
  - `traffic-pattern` is standard base64 of the `TrafficPattern` protobuf. Current mieru (`main`) and mihomo use this query. The v3.28.0 exporter did not write it yet; links that include it still import.
  - Userinfo follows Go's `url.UserPassword` escaping (`@`, `/`, `?`, and `:` are escaped; `+` is a plus). Query follows `url.Values.Encode` (`+` is a space). IPv6 hosts are bracketed.
  - Unknown query keys are ignored, matching `url.ParseQuery` plus the keys mieru reads.
- `mieru://` plus standard base64 of `ClientConfig`.
  - Every profile is imported, or only `activeProfile` when that field is set.
  - Domain name wins over IP on the same server, matching mieru.
  - Each server and each port binding becomes its own profile.
  - Extra client fields (SOCKS port, RPC, logging) are skipped. They are not a proxy outbound.

Clash Meta / mihomo subscriptions with `type: mieru` use the field names from mihomo's `MieruOption`: `server`, `port` or `port-range` (not both), `transport`, `username`, `password`, `multiplexing`, `handshake-mode`, `traffic-pattern`. A sing-box JSON subscription outbound with `"type": "mieru"` uses mbox's outbound fields (`server`, `server_port`, `server_ports`, `transport`, `username`, `password`, `multiplexing`, `traffic_pattern`, `mtu`, `handshake_mode`).

### Supported export

Share, clipboard, and QR use `mierus://` for one profile and one port binding. `profile` is `default` when the profile name is empty. `MULTIPLEXING_DEFAULT` and `HANDSHAKE_DEFAULT` are omitted, because mieru treats those as the zero value. The fragment is written only when the display name is non-empty and different from `profile`. mieru's own exporter does not write a fragment; mihomo reads one, so a name survives a round trip through this app and through mihomo.

### Rejected input

Malformed links throw `IllegalArgumentException` whose message names the problem and does not include the username, password, or the raw URL. Import logs that message only. Rejected cases include: missing profile, user, password, host, or port; a different number of `port` and `protocol` values; a protocol other than `TCP` or `UDP`; a bad port or reversed range; an MTU outside 1280–1400; an unknown multiplexing or handshake enum; a traffic pattern that is not non-empty standard base64; a `mierus://` URL with an authority port (mieru ignores that port; this app rejects it so it is not mistaken for the server port); `mieru://` that is not standard base64 or that does not contain a profile and a server.

### Not supported

- Export of `mieru://`. That link is a full client config. A proxy profile does not have the SOCKS port, RPC port, or logging settings the exporter writes.
- One NekoBox profile with mixed TCP and UDP bindings. mbox's outbound has a single `transport`. Mixed links become multiple profiles.
- UDP to a domain name. mbox returns an error for that combination (`UDP outbound can't use domain name as server address`). The outbound builder reports it before start. TCP to a domain is supported.
- IPv6 zone identifiers.
- The external `mieru-plugin` process. Profiles run inside mbox.

## Build and test

From the repository root, with Go 1.23.6 or newer as the `go` on `PATH`, the Android SDK, and NDK 25.0.8775105. gomobile writes a module whose `go` line is `1.23`; an older `go` tries to download a toolchain named `go1.23`, which is not a real release. Go 1.23.6 or newer already satisfies that line.

```sh
./run lib core
./gradlew :app:testOssDebugUnitTest :app:assembleOssDebug
```

`./run lib core` runs `get_source.sh`, then `gomobile-matsuri bind` with tags `with_conntrack,with_gvisor,with_quic,with_wireguard,with_utls,with_clash_api`. Set `GOMOBILE_TARGET` to `android/arm64`, `android/arm`, `android/386`, or `android/amd64` to bind one ABI. The debug APK uses the Android Gradle Plugin debug keystore when `KEYSTORE_PASS` is unset.

GitHub Actions workflow `.github/workflows/mbox-ci.yml` runs on pushes to `main`. It binds each ABI separately, runs the mieru share-link unit tests and the libcore Go tests, and builds an oss debug APK. A split is uploaded only when `lib/<abi>/libgojni.so` is inside the APK whose name contains that ABI. `arm64-v8a` is required. The other ABIs are attempted and omitted from the prerelease when their bind or APK check fails.

The prerelease tag is `1.4.2-mbox.1`. There is no `release.keystore` in this repository. The workflow does not read signing secrets. Debug APKs use the runner debug keystore and the application id `moe.nb4a.debug`, so they install beside a release-signed `moe.nb4a` package. A later runner can mint a different debug key; uninstall the previous prerelease if Android reports a signature conflict. Notes for that prerelease are in [MBOX_RELEASE.md](MBOX_RELEASE.md).

Host check of the embedded core, after `get_source.sh`:

```sh
cd libcore && go test -tags='with_conntrack,with_gvisor,with_quic,with_wireguard,with_utls,with_clash_api' -count=1 -run 'Mieru|EmbeddedCore'
```

Share-link fixtures live in `app/src/test/java/io/nekohasekai/sagernet/fmt/mieru/MieruFmtTest.kt`. The same `mieru://` bytes are decoded with mieru's generated protobuf in `libcore/mieru_core_test.go`.
