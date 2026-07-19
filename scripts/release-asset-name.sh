#!/usr/bin/env sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
KEY=${1:-}
VERSION=${2:-$(tr -d '[:space:]' < "$ROOT/VERSION")}

case "$KEY" in
  native.*.asset|portable.jvm.*.asset) ;;
  *) printf 'Invalid release asset key: %s\n' "$KEY" >&2; exit 2 ;;
esac
printf '%s\n' "$VERSION" | grep -Eq '^[0-9]+\.[0-9]+\.[0-9]+([+-][A-Za-z0-9.-]+)?$' || {
  printf 'Invalid release version: %s\n' "$VERSION" >&2
  exit 2
}

PATTERN=$(sed -n "s/^$KEY=//p" "$ROOT/distribution/release-assets.properties")
test -n "$PATTERN" || { printf 'Missing release asset key: %s\n' "$KEY" >&2; exit 1; }
test "$(printf '%s\n' "$PATTERN" | wc -l | tr -d '[:space:]')" = 1 || {
  printf 'Duplicate release asset key: %s\n' "$KEY" >&2
  exit 1
}
case "$PATTERN" in *'{version}'*) ;; *) printf 'Asset pattern has no version token: %s\n' "$KEY" >&2; exit 1 ;; esac

PREFIX=${PATTERN%%\{version\}*}
SUFFIX=${PATTERN#*\{version\}}
ASSET=$PREFIX$VERSION$SUFFIX
case "$ASSET" in
  *[!A-Za-z0-9._-]*|.*|*..*) printf 'Unsafe release asset name: %s\n' "$ASSET" >&2; exit 1 ;;
esac
printf '%s\n' "$ASSET"
