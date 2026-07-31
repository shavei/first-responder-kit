#!/usr/bin/env bash
#
# Creates the release signing key for First Responder Kit and prints the four
# repository secrets the release workflow needs.
#
# Android identifies an app by its signing key, not by its name. A build signed
# with a different key is a different app as far as the device is concerned, so
# it refuses to install over the old one. The key made here therefore has to
# outlive every release: back up release.jks and the passwords somewhere safe,
# because losing them means every future build has to be installed fresh.
#
#   ./tools/make-release-key.sh
#
set -euo pipefail

KEYSTORE="${1:-release.jks}"
ALIAS="${KEY_ALIAS:-first-responder-kit}"

if [ -e "$KEYSTORE" ]; then
  echo "$KEYSTORE already exists — refusing to overwrite it." >&2
  echo "Delete it deliberately, or pass a different path, if you really do want a new key." >&2
  exit 1
fi

command -v keytool > /dev/null || {
  echo "keytool not found. Install a JDK 17+ (it ships with one) and try again." >&2
  exit 1
}

# One password for the store and the key keeps the secrets simple; both are
# random, because nothing has to type them by hand. The 512 bytes are read up
# front rather than streaming /dev/urandom into `head`, which would leave the
# pipeline killed by SIGPIPE and, under `pipefail`, abort the script.
PASSWORD="$(head -c 512 /dev/urandom | LC_ALL=C tr -dc 'A-Za-z0-9' | cut -c1-32)"
[ ${#PASSWORD} -eq 32 ] || { echo "Could not read enough randomness." >&2; exit 1; }

keytool -genkeypair \
  -keystore "$KEYSTORE" \
  -storetype PKCS12 \
  -storepass "$PASSWORD" \
  -keypass "$PASSWORD" \
  -alias "$ALIAS" \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10950 \
  -dname "CN=First Responder Kit, OU=Releases, O=First Responder Kit, C=IL"

echo
echo "Created $KEYSTORE (alias $ALIAS, valid 30 years)."
echo
echo "1. Back up $KEYSTORE and the password below. They cannot be regenerated:"
echo "   a lost key means users must uninstall before they can install again."
echo
echo "2. Set these four secrets under Settings → Secrets and variables → Actions"
echo "   → New repository secret:"
echo
echo "   KEYSTORE_PASSWORD  $PASSWORD"
echo "   KEY_PASSWORD       $PASSWORD"
echo "   KEY_ALIAS          $ALIAS"
echo "   KEYSTORE_BASE64    (the long line below, no newlines)"
echo
base64 -w0 "$KEYSTORE" 2> /dev/null || base64 "$KEYSTORE" | tr -d '\n'
echo
echo
echo "3. To build signed releases locally too, write keystore.properties in the"
echo "   project root (git-ignored):"
echo
echo "   storeFile=$KEYSTORE"
echo "   storePassword=$PASSWORD"
echo "   keyAlias=$ALIAS"
echo "   keyPassword=$PASSWORD"
echo
echo "The signing key of this release build is:"
keytool -list -v -keystore "$KEYSTORE" -storepass "$PASSWORD" -alias "$ALIAS" |
  grep -E 'SHA256:' | head -1
