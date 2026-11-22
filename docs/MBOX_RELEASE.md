# NekoBox 1.4.2-mbox.1 (debug-signed test prerelease)

This build is a test prerelease of the mbox core on shad1ao/NekoBoxForAndroid. It is not signed with an upstream or Play release key.

## Install and signature

- Package id: `moe.nb4a.debug`. A release-signed `moe.nb4a` install is left in place.
- Signature: Android debug keystore created on the GitHub-hosted runner. This repository has no `release.keystore`. Actions secret names could not be listed with the available token (HTTP 403), and this workflow does not read `LOCAL_PROPERTIES`, `KEYSTORE_PASS`, `ALIAS_NAME`, or `ALIAS_PASS`.
- The debug key is not stable across runners. If Android says the package conflicts with an existing install, uninstall that previous prerelease and install this APK again.
- minSdk 21. Pick the APK whose ABI matches the device. `arm64-v8a` is the phone ABI. `x86_64` and `x86` are emulator ABIs. `armeabi-v7a` is 32-bit ARM.

## Core

- Runtime: [enfein/mbox](https://github.com/enfein/mbox) tag `v1.12.23-m.3.28.0`, commit `2ced282af6a415fbd4b6cb62aa764e79a2450141`
- mieru library: `github.com/enfein/mieru/v3` `v3.28.0`
- Reported core version: `1.12.23-m.3.28.0`
- App version: `1.4.2-mbox.1` (versionCode 235)

## Share links

Import accepts official `mierus://` and `mieru://` links from clipboard, QR, share intents, and subscriptions, plus mihomo `type: mieru` and sing-box `"type": "mieru"` outbounds. Export writes `mierus://`. Field rules and rejected input are in [docs/MBOX.md](docs/MBOX.md).

## Known limitations

- Only APKs that contain `lib/<abi>/libgojni.so` for the ABI in the file name are attached. An ABI missing from this release did not produce a verified library. Do not install an APK from an older local split that lacks that library.
- UDP to a domain name is rejected by mbox. TCP to a domain is supported.
- `mieru://` is import-only. Share/QR export is `mierus://`.
- One profile has one transport. A link with several port bindings becomes several profiles.
- CI did not exercise the Android UI on a device or emulator.

## Checksums

SHA-256 is appended by the release job.
