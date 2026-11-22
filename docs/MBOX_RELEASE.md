# NyaBox 1.4.3.1 (debug-signed test build)

This build is `1.4.3.1` of the mbox core on shad1ao/NyaBox. The product name is NyaBox. It is not signed with an upstream or Play release key. The launcher name is `NyaBox`. The repository is https://github.com/shad1ao/NyaBox.

## Install and signature

- Package id: `moe.nyabox`.
- This application id does not replace `moe.nb4a.mbox`, official `moe.nb4a`, or `moe.nb4a.debug`. Uninstall those packages yourself if you no longer want them.
- `1.4.3` uses this same package id, but each CI run signs with a new debug certificate. This APK cannot replace an installed `1.4.3`. If Android reports a signature conflict, uninstall `moe.nyabox` and install this APK.
- Signature: Android debug keystore created on the GitHub-hosted runner. This repository has no `release.keystore`. The workflow does not read `LOCAL_PROPERTIES`, `KEYSTORE_PASS`, `ALIAS_NAME`, or `ALIAS_PASS`.
- Each ABI is built on its own runner, so the four APKs do not share one debug certificate. Installing a different ABI over `moe.nyabox` can report a signature conflict. Uninstall `moe.nyabox` and install the APK again.
- minSdk 21. Pick the APK whose ABI matches the device. `arm64-v8a` is the phone ABI. `x86_64` and `x86` are emulator ABIs. `armeabi-v7a` is 32-bit ARM.

## Core

- Runtime: [enfein/mbox](https://github.com/enfein/mbox) tag `v1.12.23-m.3.28.0`, commit `2ced282af6a415fbd4b6cb62aa764e79a2450141`
- mieru library: `github.com/enfein/mieru/v3` `v3.28.0`
- Reported core version: `1.12.23-m.3.28.0`
- App version: `1.4.3.1` (versionCode 275)
- Speed and per-profile traffic use the core stats tracker. They do not require YACD. The YACD page is still the dashboard menu, and that menu stays hidden until Clash API is enabled.
- Mieru server settings use the app's existing translations. Simplified Chinese follows the official mieru terms: 端口范围, 多路复用, 握手模式, 流量模式.
- Username and password stay separate, matching mieru. The password is copied from the username only when they are already equal or the user turns that switch on. Saving Mieru settings stores the configuration name as the mieru profile. An imported profile name stays in place until that save.

## Share links

Import accepts official `mierus://` and `mieru://` links from clipboard, QR, share intents, and subscriptions, plus mihomo `type: mieru` and sing-box `"type": "mieru"` outbounds. Export writes `mierus://`. Field rules and rejected input are in [docs/MBOX.md](docs/MBOX.md).

## Known limitations

- Only APKs that contain `lib/<abi>/libgojni.so` for the ABI in the file name are attached. This release is published only when all four ABIs passed that check.
- UDP to a domain name is rejected by mbox. TCP to a domain is supported.
- `mieru://` is import-only. Share/QR export is `mierus://`.
- One profile has one transport. A link with several port bindings becomes several profiles.
- CI did not exercise the Android UI on a device or emulator.

## Checksums

SHA-256 is appended by the release job.
