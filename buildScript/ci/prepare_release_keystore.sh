#!/usr/bin/env bash
# Confirm the CI keystore can be unlocked. Prints no secret values.
# If the key entry uses the keystore password, export that as NYABOX_KEY_PASSWORD.
set -euo pipefail

trim_secret() {
  local value="$1"
  value="${value%$'\n'}"
  value="${value%$'\r'}"
  printf '%s' "$value"
}

missing=0
for name in NYABOX_KEYSTORE_FILE NYABOX_KEYSTORE_PASSWORD NYABOX_KEY_ALIAS NYABOX_KEY_PASSWORD; do
  if [ -z "${!name:-}" ]; then
    echo "::error::$name is not set"
    missing=1
  fi
done
test "$missing" -eq 0
test -s "$NYABOX_KEYSTORE_FILE"

NYABOX_KEYSTORE_PASSWORD=$(trim_secret "$NYABOX_KEYSTORE_PASSWORD")
NYABOX_KEY_ALIAS=$(trim_secret "$NYABOX_KEY_ALIAS")
NYABOX_KEY_PASSWORD=$(trim_secret "$NYABOX_KEY_PASSWORD")
export NYABOX_KEYSTORE_PASSWORD NYABOX_KEY_ALIAS NYABOX_KEY_PASSWORD

unlocks() {
  local keypass="$1"
  local dest
  dest=$(mktemp)
  if keytool -noprompt -importkeystore \
      -srckeystore "$NYABOX_KEYSTORE_FILE" \
      -srcstoretype PKCS12 \
      -srcstorepass "$NYABOX_KEYSTORE_PASSWORD" \
      -srcalias "$NYABOX_KEY_ALIAS" \
      -srckeypass "$keypass" \
      -destkeystore "$dest" \
      -deststoretype PKCS12 \
      -deststorepass "$NYABOX_KEYSTORE_PASSWORD" \
      -destkeypass "$keypass" \
      >/dev/null 2>&1; then
    rm -f "$dest"
    return 0
  fi
  rm -f "$dest"
  return 1
}

if unlocks "$NYABOX_KEY_PASSWORD"; then
  echo "PKCS12 key entry unlocks"
elif unlocks "$NYABOX_KEYSTORE_PASSWORD"; then
  NYABOX_KEY_PASSWORD="$NYABOX_KEYSTORE_PASSWORD"
  export NYABOX_KEY_PASSWORD
  echo "PKCS12 key entry unlocks with the keystore password"
else
  echo "::error::Could not unlock the PKCS12 key entry"
  exit 1
fi
