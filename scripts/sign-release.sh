#!/usr/bin/env bash
# Builds the release APK exactly as F-Droid does (unsigned), then signs it with the release key.
# F-Droid rebuilds the same commit and publishes this APK only if the two match, then the same
# APK goes on GitHub Releases under the tag.
#
#   CLASP_KEYSTORE=~/keys/clasp-release.p12 scripts/sign-release.sh
#
# apksigner asks for the keystore password; it is never passed on the command line.
set -euo pipefail

: "${CLASP_KEYSTORE:?set CLASP_KEYSTORE to the release keystore}"
KEY_ALIAS="${CLASP_KEY_ALIAS:-clasp}"
cd "$(dirname "$0")/.."

if [[ -n "$(git status --porcelain)" ]]; then
    echo "The working tree has changes; release only from a clean checkout of a tag." >&2
    exit 1
fi
TAG="$(git describe --tags --exact-match 2>/dev/null || true)"
if [[ -z "$TAG" ]]; then
    echo "HEAD is not tagged; check out the release tag first." >&2
    exit 1
fi

export JAVA_HOME="${JAVA_HOME:-/opt/android-studio/jbr}"
export PATH="$JAVA_HOME/bin:$PATH" # apksigner needs java
SDK="${ANDROID_HOME:-$(sed -n 's/^sdk\.dir=//p' local.properties 2>/dev/null)}"
BUILD_TOOLS="$(ls -d "$SDK"/build-tools/* | sort -V | tail -1)"
APKSIGNER="$BUILD_TOOLS/apksigner"

UNSIGNED=app/build/outputs/apk/release/app-release-unsigned.apk
OUT="build/release/clasp-${TAG#v}.apk"
FIRST="build/release/first-build-unsigned.apk"
mkdir -p build/release
rm -f "$OUT" "$OUT.idsig" "$FIRST"

# Build twice and sign only if both builds match: a nondeterministic build would also differ from
# F-Droid's, and F-Droid would refuse to publish the signed APK.
./gradlew --no-build-cache clean assembleRelease
cp "$UNSIGNED" "$FIRST"
./gradlew --no-build-cache clean assembleRelease
if ! cmp -s "$FIRST" "$UNSIGNED"; then
    echo "Two builds of $TAG differ, so F-Droid can't reproduce it; not signing." >&2
    exit 1
fi
rm -f "$FIRST"

# v2 + v3 only: minSdk 26 doesn't need v1, and v1 would add files F-Droid's build doesn't have.
"$APKSIGNER" sign \
    --ks "$CLASP_KEYSTORE" --ks-key-alias "$KEY_ALIAS" \
    --v1-signing-enabled false --v2-signing-enabled true --v3-signing-enabled true \
    --v4-signing-enabled false --alignment-preserved \
    --out "$OUT" "$UNSIGNED"

"$APKSIGNER" verify --print-certs "$OUT" | grep -E 'SHA-256'
sha256sum "$OUT"
echo "Signed $OUT"
