#!/usr/bin/env sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
VERSION=$(tr -d '[:space:]' < "$ROOT/VERSION")
MANIFEST="$ROOT/distribution/release-assets.properties"

grep -Fx 'schema.version=1' "$MANIFEST" >/dev/null
grep -Fx 'repository=soya7700/CarbonGate' "$MANIFEST" >/dev/null
grep -Fx 'native.darwin-arm64.asset=carbongate-{version}-darwin-arm64.tar.gz' "$MANIFEST" >/dev/null
grep -Fx 'native.linux-x64.asset=carbongate-{version}-linux-x64.tar.gz' "$MANIFEST" >/dev/null
grep -Fx 'native.windows-x64.asset=carbongate-{version}-windows-x64.zip' "$MANIFEST" >/dev/null
test "$("$ROOT/scripts/release-asset-name.sh" native.darwin-arm64.asset "$VERSION")" = \
  "carbongate-$VERSION-darwin-arm64.tar.gz"
test "$("$ROOT/scripts/release-asset-name.sh" native.linux-x64.asset "$VERSION")" = \
  "carbongate-$VERSION-linux-x64.tar.gz"
test "$("$ROOT/scripts/release-asset-name.sh" native.windows-x64.asset "$VERSION")" = \
  "carbongate-$VERSION-windows-x64.zip"
test "$("$ROOT/scripts/release-asset-name.sh" portable.jvm.tar.asset "$VERSION")" = \
  "carbongate-$VERSION.tar.gz"
test "$("$ROOT/scripts/release-asset-name.sh" portable.jvm.zip.asset "$VERSION")" = \
  "carbongate-$VERSION.zip"
grep -F 'release-asset-name.sh' "$ROOT/scripts/package-native.sh" >/dev/null
grep -F 'release-asset-name.sh' "$ROOT/scripts/package.sh" >/dev/null
grep -F 'release-asset-name.sh portable.jvm.tar.asset' "$ROOT/.github/workflows/release.yml" >/dev/null

case "$(uname -s):$(uname -m)" in
  Darwin:arm64) PLATFORM=darwin-arm64 ;;
  Linux:x86_64|Linux:amd64) PLATFORM=linux-x64 ;;
  *) printf '%s\n' 'Distribution bootstrap fixture skipped on unsupported POSIX platform.'; exit 0 ;;
esac

TEST_ROOT=$(mktemp -d "${TMPDIR:-/tmp}/carbongate-distribution-test.XXXXXX")
trap 'rm -rf "$TEST_ROOT"' EXIT HUP INT TERM
ASSET="carbongate-$VERSION-$PLATFORM.tar.gz"
PACKAGE=${ASSET%.tar.gz}
STAGE="$TEST_ROOT/stage/$PACKAGE"
RELEASE="$TEST_ROOT/releases/download/v$VERSION"
PREFIX="$TEST_ROOT/prefix"
mkdir -p "$STAGE" "$RELEASE"

cp "$ROOT/scripts/install-native.sh" "$STAGE/install.sh"
mkdir -p "$STAGE/bin"
cat > "$STAGE/bin/carbon" <<'FIXTURE'
#!/usr/bin/env sh
set -eu
case "${1:-}" in
  version) printf '%s\n' 'CarbonGate fixture' ;;
  config) mkdir -p "${CARBON_HOME:?}"; printf '%s\n' 'mode=BALANCED' > "$CARBON_HOME/carbon.conf" ;;
  *) exit 0 ;;
esac
FIXTURE
chmod +x "$STAGE/install.sh" "$STAGE/bin/carbon"
tar -C "$TEST_ROOT/stage" -czf "$RELEASE/$ASSET" "$PACKAGE"
if command -v shasum >/dev/null 2>&1; then
  HASH=$(shasum -a 256 "$RELEASE/$ASSET" | cut -d ' ' -f 1)
else
  HASH=$(sha256sum "$RELEASE/$ASSET" | cut -d ' ' -f 1)
fi
printf '%s  %s\n' "$HASH" "$ASSET" > "$RELEASE/$ASSET.sha256"

CARBON_HOME="$TEST_ROOT/carbon-home" \
CARBONGATE_ALLOW_FILE_URLS=1 \
CARBONGATE_MANIFEST_URL="file://$MANIFEST" \
CARBONGATE_RELEASE_BASE_URL="file://$TEST_ROOT/releases/download" \
  "$ROOT/scripts/install-release.sh" --version "$VERSION" --prefix "$PREFIX" >/dev/null

test -x "$PREFIX/bin/carbon"
"$PREFIX/bin/carbon" version | grep -F 'CarbonGate fixture' >/dev/null

printf '%064d  %s\n' 0 "$ASSET" > "$RELEASE/$ASSET.sha256"
if CARBON_HOME="$TEST_ROOT/rejected-home" \
  CARBONGATE_ALLOW_FILE_URLS=1 \
  CARBONGATE_MANIFEST_URL="file://$MANIFEST" \
  CARBONGATE_RELEASE_BASE_URL="file://$TEST_ROOT/releases/download" \
  "$ROOT/scripts/install-release.sh" --version "$VERSION" \
    --prefix "$TEST_ROOT/rejected-prefix" >/dev/null 2>&1; then
  printf '%s\n' 'Bootstrap accepted a release with the wrong checksum.' >&2
  exit 1
fi

printf '%s\n' 'POSIX release contract and verified bootstrap installation passed.'
