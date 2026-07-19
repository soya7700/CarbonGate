#!/usr/bin/env sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
PACKAGE="$ROOT/adapters/npm/cli"
VERSION=$(tr -d '[:space:]' < "$ROOT/VERSION")
PACKAGE_VERSION=$(node -e 'process.stdout.write(require(process.argv[1]).version)' "$PACKAGE/package.json")

test "$VERSION" = "$PACKAGE_VERSION" || {
  printf 'npm package version %s does not match VERSION %s.\n' "$PACKAGE_VERSION" "$VERSION" >&2
  exit 1
}
if test "${CARBONGATE_SKIP_NPM_TESTS:-0}" != 1; then
  "$ROOT/scripts/npm-test.sh"
fi

DESTINATION="$ROOT/build/npm"
mkdir -p "$DESTINATION"
NPM_CONFIG_CACHE="$ROOT/build/npm-package-cache" \
  npm pack --ignore-scripts --pack-destination "$DESTINATION" "$PACKAGE" >/dev/null
printf 'Packaged %s\n' "$DESTINATION/carbongate-cli-$VERSION.tgz"
