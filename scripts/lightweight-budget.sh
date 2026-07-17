#!/usr/bin/env sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
VERSION=0.2.0-test
JAR="$ROOT/build/carbongate.jar"
TAR="$ROOT/build/carbongate-$VERSION.tar.gz"
ZIP="$ROOT/build/carbongate-$VERSION.zip"
CONFIG="$ROOT/config/carbon.conf.example"

check_bytes() {
  label=$1
  path=$2
  limit=$3
  test -s "$path" || {
    printf 'Lightweight budget input is missing: %s\n' "$path" >&2
    exit 1
  }
  bytes=$(wc -c < "$path" | tr -d '[:space:]')
  test "$bytes" -le "$limit" || {
    printf '%s exceeds its lightweight budget: %s > %s bytes\n' "$label" "$bytes" "$limit" >&2
    exit 1
  }
  printf '%s: %s/%s bytes\n' "$label" "$bytes" "$limit"
}

check_bytes 'Base JAR' "$JAR" 204800
check_bytes 'Base tar.gz' "$TAR" 512000
check_bytes 'Base zip' "$ZIP" 512000
check_bytes 'Default configuration' "$CONFIG" 4096
test -s "$ROOT/build/carbongate-enterprise-host.jar" || "$ROOT/scripts/build-enterprise.sh" >/dev/null
check_bytes 'Enterprise Component Host' "$ROOT/build/carbongate-enterprise-host.jar" 153600

if jar --list --file "$JAR" | grep -q '^io/carbongate/enterprise/'; then
  printf 'Enterprise implementation leaked into the base JAR.\n' >&2
  exit 1
fi

printf 'CarbonGate lightweight budgets passed.\n'
