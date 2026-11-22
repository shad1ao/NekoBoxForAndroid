#!/usr/bin/env bash
# Confirm every APK in a directory is signed by the NyaBox release certificate
# and is not a debuggable build. The certificate digest is public.
set -euo pipefail

if [ "$#" -ne 1 ]; then
  echo "usage: verify_release_cert.sh <apk-dir>" >&2
  exit 2
fi

apk_dir="$1"
if [ -z "${ANDROID_HOME:-}" ]; then
  echo "ANDROID_HOME is not set" >&2
  exit 1
fi

apksigner="$ANDROID_HOME/build-tools/35.0.1/apksigner"
aapt="$ANDROID_HOME/build-tools/35.0.1/aapt"
expected="784c64c6c8e7bf7180c59277d6f6a7cb990550fb89f610a4685169e55efb3a29"
shopt -s nullglob
apks=("$apk_dir"/*.apk)
if [ "${#apks[@]}" -eq 0 ]; then
  echo "no APKs in $apk_dir" >&2
  exit 1
fi

for apk in "${apks[@]}"; do
  base=$(basename "$apk")
  digest=$("$apksigner" verify --print-certs "$apk" | sed -n 's/.*SHA-256 digest: //p' | head -n 1)
  got=$(printf '%s' "$digest" | tr -d '[:space:]' | tr '[:upper:]' '[:lower:]' | tr -d ':')
  if [ "$got" != "$expected" ]; then
    echo "$base signing certificate does not match the NyaBox release key" >&2
    echo "measured SHA-256: ${got:-missing}" >&2
    exit 1
  fi
  badging=$("$aapt" dump badging "$apk")
  if printf '%s\n' "$badging" | grep -q "application-debuggable"; then
    echo "$base is debuggable" >&2
    exit 1
  fi
  echo "$base certificate SHA-256 matches the NyaBox release key"
done
