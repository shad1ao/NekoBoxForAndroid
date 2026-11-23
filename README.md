# NyaBox

[![API](https://img.shields.io/badge/API-21%2B-brightgreen.svg?style=flat)](https://android-arsenal.com/api?level=21)
[![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-orange.svg)](https://www.gnu.org/licenses/gpl-3.0)

本项目包含 AI 辅助生成的代码和文档，可能存在 AI slop、错误或未经充分验证的实现，请自行审查并谨慎使用。

## 下载与安装

安装包在 [GitHub Releases](https://github.com/shad1ao/NyaBox/releases)。写作本文时，已发布的最新标签是 `1.4.3.3`（APK 的 versionCode 是 285）。每个标签附带四个 ABI，文件名形如 `NyaBox-1.4.3.3-arm64-v8a.apk`：

| ABI | 适用 |
| --- | --- |
| `arm64-v8a` | 常见的 64 位 ARM 手机 |
| `armeabi-v7a` | 32 位 ARM |
| `x86_64` | 64 位模拟器 |
| `x86` | 32 位模拟器 |

最低系统版本是 Android 5.0（API 21）。请选择与设备 ABI 一致的 APK。

- 包名是 `moe.nyabox`。它不能替换 `moe.nb4a`、`moe.nb4a.debug` 或 `moe.nb4a.mbox`。
- `1.4.3` 和 `1.4.3.1` 使用调试证书签名。`1.4.3.3` 不能覆盖安装这两个版本，需要先卸载 `moe.nyabox`。
- `1.4.3.2` 与 `1.4.3.3` 使用同一张发布证书，可以互相覆盖安装。四个 ABI 的发布包使用同一张证书。
- 本仓库不在 Google Play 发布。旧的 NekoBox Play 版本自 2024 年 5 月起已由第三方控制，而且不是开源构建，不是本仓库的安装包。

`1.4.3.3` 的安装说明、签名范围和已知限制见 [docs/MBOX_RELEASE.md](docs/MBOX_RELEASE.md)。

## 能做什么

NyaBox 在 Android 上管理代理配置，并通过 mbox 建立连接。配置可以手动添加，也可以从剪贴板、二维码、分享或订阅导入。订阅只解析节点；分流规则等其余内容会被忽略。

内核直接支持的协议：

- SOCKS（4 / 4a / 5）、HTTP(S)、SSH
- Shadowsocks、ShadowTLS
- VMess、VLESS、Trojan、AnyTLS、TUIC
- Hysteria 1 / 2（UDP）、WireGuard
- Mieru

分享链接包括 `socks://`、`socks4://`、`socks4a://`、`socks5://`、`http://`、`https://`、`ss://`、`vmess://`、`vless://`、`trojan://`、`hysteria://`、`hysteria2://`、`hy2://`、`tuic://`、`anytls://`，以及本应用自己的 `sn://`。Mieru 可以导入官方的 `mierus://` 和 `mieru://`；导出使用 `mierus://`。链接里的空格写成 `%20`。

订阅还可以解析：

- Clash / Clash Meta 的 `proxies`（`socks5`、`http`、`ss`、`vmess`、`vless`、`trojan`、`mieru`、`anytls`、`hysteria`、`hysteria2`、`tuic`）
- sing-box JSON 里的出站。`type: mieru` 会变成 Mieru 配置，其余出站保留为 sing-box JSON
- WireGuard 的 `ini` 配置
- 纯文本或 Base64 的分享链接列表

Trojan-Go 和 NaïveProxy 不在内核里，运行前需要安装上游插件。应用给出的入口是 [Trojan-Go 的上游页面](https://matsuridayo.github.io/) 和 [NaïveProxy 插件](https://github.com/MatsuriDayo/plugins/releases?q=naive)。Hysteria 只有在传输不是 UDP 时才使用外部插件，对应入口是 [Hysteria 插件](https://github.com/MatsuriDayo/plugins/releases?q=Hysteria)。

打开 Clash API 之后，应用在 `127.0.0.1:9090` 提供 Clash API，并使用内置的 [Yacd-meta](https://github.com/MetaCubeX/Yacd-meta) 面板。默认地址是 `http://127.0.0.1:9090/ui`。

## 从源码构建

需要 JDK 21、Go 1.26.11，以及 Android SDK。CI 安装的组件是 `platforms;android-35`、`build-tools;36.0.0`、`build-tools;35.0.1` 和 NDK `25.0.8775105`，见 [buildScript/ci/setup_android_sdk.sh](buildScript/ci/setup_android_sdk.sh)。Gradle 使用仓库里的 wrapper（9.7.0）。设置 `ANDROID_HOME`。Go 使用本机工具链（`GOTOOLCHAIN=local`）。

核心源码会放到仓库的上一级目录，而不是仓库里面。`mbox` 和 `libneko` 因此与本仓库并列。

```bash
bash buildScript/lib/core/init.sh
GOMOBILE_TARGET=android/arm64 bash buildScript/lib/core/build.sh
./gradlew :app:assembleOssDebug
```

`init.sh` 会检出固定的 mbox 提交，并安装本仓库使用的 gomobile / gobind。不要另外执行 `gomobile init`。`build.sh` 生成 `app/libs/libcore.aar`。不设置 `GOMOBILE_TARGET` 时会编译全部四个 ABI。

调试包出现在 `app/build/outputs/apk/oss/debug/`，文件名是 `NyaBox-<版本>-<abi>.apk`。调试构建不需要发布签名。Android 单元测试：

```bash
./gradlew :app:testOssDebugUnitTest
```

推送到 `main` 时，[`.github/workflows/mbox-ci.yml`](.github/workflows/mbox-ci.yml) 会绑定 libcore 并组装四个 ABI 的发布包。已经存在的版本标签不会被重新发布。

## 许可证

本程序以 GNU General Public License v3 或更高版本授权，见 [LICENSE](LICENSE)。没有质量保证。上游项目使用它们各自的许可证。

## 致谢

内核：

- [enfein/mbox](https://github.com/enfein/mbox) `v1.12.23-m.3.28.0`
- [enfein/mieru](https://github.com/enfein/mieru)（mieru 协议和分享链接）
- [SagerNet/sing-box](https://github.com/SagerNet/sing-box)

Android 界面来自 [MatsuriDayo/NekoBoxForAndroid](https://github.com/MatsuriDayo/NekoBoxForAndroid)，并继承自：

- [shadowsocks/shadowsocks-android](https://github.com/shadowsocks/shadowsocks-android)
- [SagerNet/SagerNet](https://github.com/SagerNet/SagerNet)

面板：

- [Yacd-meta](https://github.com/MetaCubeX/Yacd-meta)
