#!/usr/bin/env sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
OUT="$ROOT/build/native"
TEMP="$ROOT/build/native-tmp"

command -v native-image >/dev/null 2>&1 || {
  printf '%s\n' 'native-image was not found. Use GraalVM Community Native Image to build the local CLI.' >&2
  exit 1
}

"$ROOT/scripts/build.sh" >/dev/null
rm -rf "$OUT" "$TEMP"
mkdir -p "$OUT" "$TEMP"
(
  cd "$OUT"
  TMPDIR="$TEMP" native-image -J-Djava.io.tmpdir="$TEMP" -O2 \
    -jar "$ROOT/build/carbongate.jar" carbon
)

case "$(uname -s)" in
  MINGW*|MSYS*|CYGWIN*) BINARY="$OUT/carbon.exe" ;;
  *) BINARY="$OUT/carbon" ;;
esac
test -x "$BINARY" || test -s "$BINARY" || {
  printf 'Native Image did not create the expected executable: %s\n' "$BINARY" >&2
  exit 1
}
printf 'Built native CarbonGate executable %s\n' "$BINARY"
