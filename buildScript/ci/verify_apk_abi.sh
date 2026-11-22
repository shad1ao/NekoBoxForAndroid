#!/usr/bin/env bash
# Copy an APK only when its file name and libgojni.so agree on the ABI.
# Other splits in the same directory are left behind. A split with no
# matching native library is never copied.
set -euo pipefail

if [ "$#" -ne 3 ]; then
  echo "usage: verify_apk_abi.sh <abi> <apk-dir> <output-dir>" >&2
  exit 2
fi

expected="$1"
apk_dir="$2"
out_dir="$3"
mkdir -p "$out_dir"

abi_of() {
  case "$1" in
    *arm64-v8a*) echo arm64-v8a ;;
    *armeabi-v7a*) echo armeabi-v7a ;;
    *x86_64*) echo x86_64 ;;
    *x86*) echo x86 ;;
    *) echo "" ;;
  esac
}

found=""
shopt -s nullglob
for apk in "$apk_dir"/*.apk; do
  base=$(basename "$apk")
  abi=$(abi_of "$base")
  if [ -z "$abi" ]; then
    echo "reject $base: file name has no known ABI" >&2
    continue
  fi
  if unzip -l "$apk" | grep -q "lib/${abi}/libgojni.so"; then
    if [ "$abi" = "$expected" ]; then
      cp "$apk" "$out_dir/$base"
      found="$out_dir/$base"
      echo "keep $base: lib/${abi}/libgojni.so"
    else
      echo "skip $base: ABI $abi is not the APK requested for this job"
    fi
  else
    echo "reject $base: lib/${abi}/libgojni.so is absent" >&2
  fi
done

if [ -z "$found" ]; then
  echo "no $expected APK contains lib/${expected}/libgojni.so" >&2
  exit 1
fi
