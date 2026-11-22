#!/bin/bash

set -euo pipefail

DIR=app/src/main/assets/sing-box
rm -rf $DIR
mkdir -p $DIR
cd $DIR

# Four APK jobs call this together. An unauthenticated response can be empty
# under the shared runner rate limit, and a pipeline without pipefail used to
# turn that into a blank tag and a 404 on the asset URL.
get_latest_release() {
  local repo="$1"
  local attempt body tag
  local -a auth=()
  local token="${GITHUB_TOKEN:-${GH_TOKEN:-}}"
  if [ -n "$token" ]; then
    auth=(-H "Authorization: Bearer ${token}")
  fi
  for attempt in 1 2 3 4 5; do
    body=$(curl --silent --show-error --fail --retry 3 --retry-all-errors \
      -H "Accept: application/vnd.github+json" \
      -H "X-GitHub-Api-Version: 2022-11-28" \
      "${auth[@]}" \
      "https://api.github.com/repos/${repo}/releases/latest") || body=""
    tag=$(printf '%s\n' "$body" | sed -n 's/.*"tag_name": *"\([^"]*\)".*/\1/p' | head -n 1)
    if [ -n "$tag" ]; then
      printf '%s\n' "$tag"
      return 0
    fi
    sleep $((attempt * 2))
  done
  echo "could not read the latest release tag for ${repo}" >&2
  return 1
}

####
VERSION_GEOIP=`get_latest_release "SagerNet/sing-geoip"`
echo VERSION_GEOIP=$VERSION_GEOIP
echo -n $VERSION_GEOIP > geoip.version.txt
curl -fLSsO https://github.com/SagerNet/sing-geoip/releases/download/$VERSION_GEOIP/geoip.db
xz -9 geoip.db

####
VERSION_GEOSITE=`get_latest_release "SagerNet/sing-geosite"`
echo VERSION_GEOSITE=$VERSION_GEOSITE
echo -n $VERSION_GEOSITE > geosite.version.txt
curl -fLSsO https://github.com/SagerNet/sing-geosite/releases/download/$VERSION_GEOSITE/geosite.db
xz -9 geosite.db
