#!/bin/bash
set -e

source "buildScript/init/env.sh"
ENV_NB4A=1
source "buildScript/lib/core/get_source_env.sh"
ROOT=$(pwd)
pushd ..

####
# Runtime core is enfein/mbox, checked out at COMMIT_MBOX, then the
# NekoBox compatibility patch is applied. The patch does not replace
# mieru or the sing-box implementation; it restores the small hooks
# libcore already depends on (nekoutils, boxapi, platform dialer).

if [ ! -d "mbox/.git" ]; then
  rm -rf mbox
  git clone --no-checkout https://github.com/enfein/mbox.git mbox
fi
pushd mbox
git fetch --depth 1 origin tag "$MBOX_TAG"
git reset --hard "$COMMIT_MBOX"
git clean -fd
git apply --check "$ROOT/buildScript/lib/core/mbox-compat/neko-compat.patch"
git apply "$ROOT/buildScript/lib/core/mbox-compat/neko-compat.patch"
popd

####

if [ ! -d "libneko" ]; then
  git clone --no-checkout https://github.com/MatsuriDayo/libneko.git
fi
pushd libneko
git checkout "$COMMIT_LIBNEKO"
popd

####

popd
