# NyaBox

[![API](https://img.shields.io/badge/API-21%2B-brightgreen.svg?style=flat)](https://android-arsenal.com/api?level=21)
[![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-orange.svg)](https://www.gnu.org/licenses/gpl-3.0)

本项目包含 AI 辅助生成的代码和文档，可能存在 AI slop、错误或未经充分验证的实现，请自行审查并谨慎使用。

## 下载与安装

安装包在 [GitHub Releases](https://github.com/shad1ao/NyaBox/releases)。每个标签附带四个 ABI，文件名形如 `NyaBox-<版本>-<abi>.apk`：

| ABI | 适用 |
| --- | --- |
| `arm64-v8a` | 常见的 64 位 ARM 手机 |
| `armeabi-v7a` | 32 位 ARM |
| `x86_64` | 64 位模拟器 |
| `x86` | 32 位模拟器 |

最低系统版本是 Android 5.0（API 21）。请选择与设备 ABI 一致的 APK。

## 能做什么

NyaBox 是适用于 Android 的代理软件。

内核直接支持的协议：

- SOCKS（4 / 4a / 5）、HTTP(S)、SSH
- Shadowsocks、ShadowTLS
- VMess、VLESS、Trojan、AnyTLS、TUIC
- Hysteria 1 / 2（UDP）、WireGuard
- Mieru

订阅还可以解析：

- Clash / Clash Meta 的 `proxies`（`socks5`、`http`、`ss`、`vmess`、`vless`、`trojan`、`mieru`、`anytls`、`hysteria`、`hysteria2`、`tuic`）
- sing-box JSON 里的出站。`type: mieru` 会变成 Mieru 配置，其余出站保留为 sing-box JSON
- WireGuard 的 `ini` 配置
- 纯文本或 Base64 的分享链接列表

Trojan-Go 和 NaïveProxy 不在内核里，运行前需要安装上游插件。应用给出的入口是 [Trojan-Go 的上游页面](https://matsuridayo.github.io/) 和 [NaïveProxy 插件](https://github.com/MatsuriDayo/plugins/releases?q=naive)。Hysteria 只有在传输不是 UDP 时才使用外部插件，对应入口是 [Hysteria 插件](https://github.com/MatsuriDayo/plugins/releases?q=Hysteria)。

打开 Clash API 之后，应用在 `127.0.0.1:9090` 提供 Clash API，并使用内置的 [Yacd-meta](https://github.com/MetaCubeX/Yacd-meta) 面板。默认地址是 `http://127.0.0.1:9090/ui`。

## 从源码构建

需要 JDK 21、Go 1.26.11 和 Android SDK。设置 `ANDROID_HOME`，`GOTOOLCHAIN=local`。

```bash
export GOTOOLCHAIN=local
bash buildScript/lib/core/init.sh
GOMOBILE_TARGET=android/arm64 bash buildScript/lib/core/build.sh
./gradlew :app:assembleOssDebug
```

## 许可证

本程序以 GNU General Public License v3 或更高版本授权，见 [LICENSE](LICENSE)。没有质量保证。上游项目使用它们各自的许可证。

## 致谢

内核：

- [enfein/mbox](https://github.com/enfein/mbox) `v1.12.23-m.3.28.0`
- [enfein/mieru](https://github.com/enfein/mieru)
- [SagerNet/sing-box](https://github.com/SagerNet/sing-box)

Android 界面来自 [MatsuriDayo/NekoBoxForAndroid](https://github.com/MatsuriDayo/NekoBoxForAndroid)，并继承自：

- [shadowsocks/shadowsocks-android](https://github.com/shadowsocks/shadowsocks-android)
- [SagerNet/SagerNet](https://github.com/SagerNet/SagerNet)

面板：

- [Yacd-meta](https://github.com/MetaCubeX/Yacd-meta)
