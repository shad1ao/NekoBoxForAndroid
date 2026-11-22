#!/bin/bash

source ./env_java.sh || true
source ../buildScript/init/env_ndk.sh

BUILD=".build"

rm -rf $BUILD/android \
  $BUILD/java \
  $BUILD/javac-output \
  $BUILD/src

if [ -z "$GOPATH" ]; then
  GOPATH=$(go env GOPATH)
fi

source ../buildScript/lib/core/get_source_env.sh
export GOBIND=gobind-matsuri
# -X overrides constant.Version. The compat patch sets the same string so
# non-gomobile builds (go test) report the pinned mbox revision too.
# GOMOBILE_TARGET limits the bind to one ABI (android/arm64, android/arm,
# android/386, android/amd64). Unset builds every ABI.
bind_args=(
  bind
  -v
  -androidapi 21
  -cache "$(realpath "$BUILD")"
  -trimpath
  -ldflags="-s -w -X github.com/sagernet/sing-box/constant.Version=${MBOX_VERSION}"
  -tags='with_conntrack,with_gvisor,with_quic,with_wireguard,with_utls,with_clash_api'
)
if [ -n "${GOMOBILE_TARGET:-}" ]; then
  bind_args+=(-target "$GOMOBILE_TARGET")
fi
"$GOPATH"/bin/gomobile-matsuri "${bind_args[@]}" . || exit 1
rm -r libcore-sources.jar

proj=../app/libs
mkdir -p $proj
cp -f libcore.aar $proj
echo ">> install $(realpath $proj)/libcore.aar"
