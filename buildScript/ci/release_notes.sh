#!/usr/bin/env bash
# Append the ABI inventory and SHA-256 sums to the prerelease notes.
set -euo pipefail

if [ "$#" -ne 2 ]; then
  echo "usage: release_notes.sh <apk-dir> <output-file>" >&2
  exit 2
fi

apk_dir="$1"
out="$2"
root=$(cd "$(dirname "$0")/../.." && pwd)
version=$(sed -n 's/^VERSION_NAME=//p' "$root/nb4a.properties")
if [ -z "$version" ]; then
  echo "release notes: VERSION_NAME is missing from nb4a.properties" >&2
  exit 1
fi
echo "# NyaBox ${version}" > "$out"

# Changes are the commit subjects since the previous tag. The version being
# published is not tagged yet, so describe points at that previous release.
git -C "$root" fetch --tags --force origin >/dev/null 2>&1 || true
prev=$(git -C "$root" describe --tags --abbrev=0 2>/dev/null || true)
if [ -z "$prev" ]; then
  echo "release notes: no previous tag; refusing to invent a changelog" >&2
  exit 1
fi
if [ "$(git -C "$root" rev-parse HEAD)" = "$(git -C "$root" rev-parse "${prev}^{}")" ]; then
  echo "release notes: HEAD is already ${prev}; refusing to publish without changes" >&2
  exit 1
fi
mapfile -t subjects < <(git -C "$root" log --no-merges --pretty=format:%s "${prev}..HEAD")
if [ "${#subjects[@]}" -eq 0 ]; then
  mapfile -t subjects < <(git -C "$root" log --pretty=format:%s "${prev}..HEAD")
fi
if [ "${#subjects[@]}" -eq 0 ]; then
  echo "release notes: no commits since ${prev}" >&2
  exit 1
fi
{
  echo
  echo "## Changes since ${prev}"
  echo
  for subject in "${subjects[@]}"; do
    [ -n "$subject" ] || continue
    echo "- ${subject}"
  done
} >> "$out"

{
  echo
  echo "## Architectures"
  echo
} >> "$out"

abi_of() {
  case "$1" in
    *arm64-v8a*) echo arm64-v8a ;;
    *armeabi-v7a*) echo armeabi-v7a ;;
    *x86_64*) echo x86_64 ;;
    *x86*) echo x86 ;;
    *) echo "" ;;
  esac
}

declare -A found_by_abi=()
while IFS= read -r apk; do
  abi=$(abi_of "$(basename "$apk")")
  if [ -n "$abi" ]; then
    found_by_abi["$abi"]=$(basename "$apk")
  fi
done < <(find "$apk_dir" -type f -name '*.apk' | sort)

included=0
for abi in arm64-v8a armeabi-v7a x86_64 x86; do
  if [ -n "${found_by_abi[$abi]:-}" ]; then
    echo "- ${abi}: ${found_by_abi[$abi]}" >> "$out"
    included=$((included + 1))
  else
    echo "- ${abi}: not published (no verified lib/${abi}/libgojni.so APK)" >> "$out"
  fi
done

for abi in arm64-v8a armeabi-v7a x86_64 x86; do
  if [ -z "${found_by_abi[$abi]:-}" ]; then
    echo "refusing to publish without a verified ${abi} APK" >&2
    exit 1
  fi
done

sums=$(mktemp)
(
  cd "$apk_dir"
  find . -type f -name '*.apk' -printf '%P\n' | sort | while IFS= read -r name; do
    sha256sum "$name"
  done
) | tee "$sums" > "$apk_dir/SHA256SUMS"

{
  echo
  echo "## SHA-256"
  echo
  echo '```'
  cat "$sums"
  echo '```'
} >> "$out"
rm -f "$sums"
