#!/usr/bin/env sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
VERSION=$(tr -d '[:space:]' < "$ROOT/VERSION")
HOME_DIR="$ROOT/build/native-test-home"

"$ROOT/scripts/build-native.sh" >/dev/null
case "$(uname -s)" in
  MINGW*|MSYS*|CYGWIN*) CARBON="$ROOT/build/native/carbon.exe" ;;
  *) CARBON="$ROOT/build/native/carbon" ;;
esac

rm -rf "$HOME_DIR"
CARBON_HOME="$HOME_DIR" "$CARBON" version | grep -F "CarbonGate $VERSION (native, Java 21 source)" >/dev/null
CARBON_HOME="$HOME_DIR" "$CARBON" config init >/dev/null
CARBON_HOME="$HOME_DIR" "$CARBON" mode set warn >/dev/null
CARBON_HOME="$HOME_DIR" "$CARBON" mode show | grep -F '"mode":"warn"' >/dev/null
CARBON_HOME="$HOME_DIR" "$CARBON" rules | grep -F '"shellRules"' >/dev/null
CARBON_HOME="$HOME_DIR" "$CARBON" redact 'token=secret-value' | grep -F '<SECRET:ASSIGNED_SECRET:' >/dev/null

bytes=$(wc -c < "$CARBON" | tr -d '[:space:]')
test "$bytes" -le 31457280 || {
  printf 'Native executable exceeds the 30 MiB lightweight budget: %s bytes\n' "$bytes" >&2
  exit 1
}
printf 'Native CarbonGate verification passed: %s bytes, no Java runtime required.\n' "$bytes"
