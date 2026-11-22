#!/usr/bin/env bash
# Install the NDK and SDK packages this repo's libcore bind expects.
set -euo pipefail

if [ -z "${ANDROID_HOME:-}" ]; then
  if [ -d "$HOME/Android/Sdk" ]; then
    ANDROID_HOME="$HOME/Android/Sdk"
  elif [ -d "$HOME/.local/lib/android/sdk" ]; then
    ANDROID_HOME="$HOME/.local/lib/android/sdk"
  else
    ANDROID_HOME="$HOME/Android/Sdk"
  fi
fi
export ANDROID_HOME
mkdir -p "$ANDROID_HOME"

sdkmanager=""
if [ -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]; then
  sdkmanager="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
else
  found=$(find "$ANDROID_HOME" -type f -name sdkmanager 2>/dev/null | head -n 1 || true)
  if [ -n "$found" ]; then
    sdkmanager="$found"
  fi
fi

if [ -z "$sdkmanager" ]; then
  tmp=$(mktemp -d)
  curl -fsSL -o "$tmp/cmdtools.zip" https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
  unzip -q "$tmp/cmdtools.zip" -d "$tmp"
  rm -rf "$ANDROID_HOME/cmdline-tools/latest"
  mkdir -p "$ANDROID_HOME/cmdline-tools"
  mv "$tmp/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
  rm -rf "$tmp"
  sdkmanager="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
fi

yes | "$sdkmanager" --sdk_root="$ANDROID_HOME" --licenses >/dev/null || true
"$sdkmanager" --sdk_root="$ANDROID_HOME" \
  "ndk;25.0.8775105" \
  "platforms;android-35" \
  "build-tools;35.0.1"
test -f "$ANDROID_HOME/ndk/25.0.8775105/source.properties"

if [ -n "${GITHUB_ENV:-}" ]; then
  {
    echo "ANDROID_HOME=$ANDROID_HOME"
    echo "ANDROID_SDK_ROOT=$ANDROID_HOME"
    echo "ANDROID_NDK_HOME=$ANDROID_HOME/ndk/25.0.8775105"
    echo "NDK=$ANDROID_HOME/ndk/25.0.8775105"
  } >> "$GITHUB_ENV"
fi

echo "Android SDK ready at $ANDROID_HOME"
