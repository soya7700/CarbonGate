#!/usr/bin/env sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
VERSION=0.2.0-test

CARBON_SKIP_TESTS=1 "$ROOT/scripts/package.sh" "$VERSION" >/dev/null

TAR="$ROOT/build/carbongate-$VERSION.tar.gz"
ZIP="$ROOT/build/carbongate-$VERSION.zip"
test -s "$TAR"
test -s "$ZIP"

tar -tzf "$TAR" | grep -F "carbongate-$VERSION/bin/carbongate.jar" >/dev/null
tar -tzf "$TAR" | grep -F "carbongate-$VERSION/install.sh" >/dev/null
tar -tzf "$TAR" | grep -F "carbongate-$VERSION/install.ps1" >/dev/null
jar --list --file "$ZIP" | grep -F "carbongate-$VERSION/bin/carbon.cmd" >/dev/null
jar --list --file "$ZIP" | grep -F "carbongate-$VERSION/README-CN.md" >/dev/null
jar --list --file "$ZIP" | grep -F "carbongate-$VERSION/skills/carbongate/SKILL.md" >/dev/null

INSTALL_PREFIX="$ROOT/build/package-test-prefix"
CARBON_HOME="$ROOT/build/package-test-home" \
  "$ROOT/build/carbongate-$VERSION/install.sh" --prefix "$INSTALL_PREFIX" >/dev/null
"$INSTALL_PREFIX/bin/carbon" version | grep -F 'CarbonGate 0.2.0 (Java 21)' >/dev/null
CARBON_HOME="$ROOT/build/package-test-home" \
  "$INSTALL_PREFIX/bin/carbon" config set mode WARN >/dev/null
CARBON_HOME="$ROOT/build/package-test-home" \
  "$ROOT/build/carbongate-$VERSION/install.sh" --prefix "$INSTALL_PREFIX" >/dev/null
CARBON_HOME="$ROOT/build/package-test-home" \
  "$INSTALL_PREFIX/bin/carbon" mode show | grep -F '"mode":"warn"' >/dev/null
grep -F 'carbongate.jar' "$ROOT/build/carbongate-$VERSION/install.ps1" >/dev/null

printf 'Package contents verified for tar.gz and zip.\n'
