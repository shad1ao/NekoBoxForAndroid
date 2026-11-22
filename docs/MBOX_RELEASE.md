# NekoBox 1.4.2-mbox.4 (debug-signed test prerelease)

This build is a test prerelease of the mbox core on shad1ao/NekoBoxForAndroid. It is not signed with an upstream or Play release key. The launcher name is `NekoBox mbox`.

## Install and signature

- Package id: `moe.nb4a.mbox`.
- It installs beside official `moe.nb4a` and beside the previous prerelease `moe.nb4a.debug` (`1.4.2-mbox.1`). Android will not update either of those in place. Uninstall `moe.nb4a.debug` yourself if you no longer want that test build.
- `1.4.2-mbox.2` and `1.4.2-mbox.3` use this same package id, but each CI run signs with a new debug certificate. The same application id does not mean this APK can replace the previous one. If Android reports a signature conflict, uninstall `moe.nb4a.mbox` and install this APK.
- Signature: Android debug keystore created on the GitHub-hosted runner. This repository has no `release.keystore`. The workflow does not read `LOCAL_PROPERTIES`, `KEYSTORE_PASS`, `ALIAS_NAME`, or `ALIAS_PASS`.
- Each ABI is built on its own runner, so the four APKs do not share one debug certificate. Installing a different ABI over this package can report a signature conflict. Uninstall `moe.nb4a.mbox` and install the APK again.
- minSdk 21. Pick the APK whose ABI matches the device. `arm64-v8a` is the phone ABI. `x86_64` and `x86` are emulator ABIs. `armeabi-v7a` is 32-bit ARM.

## Core

- Runtime: [enfein/mbox](https://github.com/enfein/mbox) tag `v1.12.23-m.3.28.0`, commit `2ced282af6a415fbd4b6cb62aa764e79a2450141`
- mieru library: `github.com/enfein/mieru/v3` `v3.28.0`
- Reported core version: `1.12.23-m.3.28.0`
- App version: `1.4.2-mbox.4` (versionCode 250)
- Mieru server settings use the app's existing translations. Simplified Chinese follows the official mieru terms: 端口范围, Mieru 配置名, 多路复用, 握手模式, 流量模式.
- Username and password stay separate, matching mieru. The password is copied from the username only when they are already equal or the user turns that switch on. The Mieru profile field is hidden when it matches the display name.

## Share links

Import accepts official `mierus://` and `mieru://` links from clipboard, QR, share intents, and subscriptions, plus mihomo `type: mieru` and sing-box `"type": "mieru"` outbounds. Export writes `mierus://`. Field rules and rejected input are in [docs/MBOX.md](docs/MBOX.md).

## Known limitations

- Only APKs that contain `lib/<abi>/libgojni.so` for the ABI in the file name are attached. An ABI missing from this release did not produce a verified library.
- UDP to a domain name is rejected by mbox. TCP to a domain is supported.
- `mieru://` is import-only. Share/QR export is `mierus://`.
- One profile has one transport. A link with several port bindings becomes several profiles.
- CI did not exercise the Android UI on a device or emulator.

## Checksums

SHA-256 is appended by the release job.
