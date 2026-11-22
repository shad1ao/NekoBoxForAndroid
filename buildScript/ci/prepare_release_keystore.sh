#!/usr/bin/env bash
# Confirm the CI PKCS12 can be unlocked. Prints no secret values.
# Gradle passes the key password separately. Java PKCS12 entries are often
# protected by the keystore password, so fall back to that when it unlocks the key.
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

probe_dir=$(mktemp -d)
cat > "$probe_dir/Probe.java" <<'EOF'
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.UnrecoverableKeyException;

public class Probe {
    public static void main(String[] args) throws Exception {
        String file = args[0];
        String alias = System.getenv("NYABOX_KEY_ALIAS");
        char[] store = System.getenv("NYABOX_KEYSTORE_PASSWORD").toCharArray();
        char[] key = System.getenv("NYABOX_KEY_PASSWORD").toCharArray();
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (FileInputStream in = new FileInputStream(file)) {
            ks.load(in, store);
        } catch (Exception e) {
            System.out.println("STORE_FAIL");
            return;
        }
        try {
            if (ks.getKey(alias, key) == null) {
                System.out.println("NO_KEY");
                return;
            }
            System.out.println("KEY_OK");
        } catch (UnrecoverableKeyException e) {
            try {
                if (ks.getKey(alias, store) == null) {
                    System.out.println("NO_KEY");
                    return;
                }
                System.out.println("STORE_OK");
            } catch (UnrecoverableKeyException e2) {
                System.out.println("LOCKED");
            }
        }
    }
}
EOF
javac "$probe_dir/Probe.java"
result=$(java -cp "$probe_dir" Probe "$NYABOX_KEYSTORE_FILE")
rm -rf "$probe_dir"
case "$result" in
  KEY_OK)
    echo "PKCS12 key entry unlocks"
    ;;
  STORE_OK)
    NYABOX_KEY_PASSWORD="$NYABOX_KEYSTORE_PASSWORD"
    export NYABOX_KEY_PASSWORD
    echo "PKCS12 key entry unlocks with the keystore password"
    ;;
  *)
    echo "::error::Could not unlock the PKCS12 key entry"
    exit 1
    ;;
esac
