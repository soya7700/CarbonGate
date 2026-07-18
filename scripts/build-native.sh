#!/usr/bin/env sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
OUT="$ROOT/build/native"
TEMP="$ROOT/build/native-tmp"

if command -v native-image >/dev/null 2>&1; then
  NATIVE_IMAGE=native-image
elif command -v native-image.cmd >/dev/null 2>&1; then
  NATIVE_IMAGE=native-image.cmd
else
  NATIVE_IMAGE=
  for runtime_home in "${GRAALVM_HOME:-}" "${JAVA_HOME:-}"; do
    test -n "$runtime_home" || continue
    case "$(uname -s)" in
      MINGW*|MSYS*|CYGWIN*)
        command -v cygpath >/dev/null 2>&1 && runtime_home=$(cygpath -u "$runtime_home")
        ;;
    esac
    for executable in native-image native-image.cmd; do
      candidate="$runtime_home/bin/$executable"
      if test -f "$candidate"; then
        NATIVE_IMAGE=$candidate
        break 2
      fi
    done
  done
  test -n "$NATIVE_IMAGE" || {
    printf '%s\n' 'native-image was not found. Use GraalVM Community Native Image to build the local CLI.' >&2
    exit 1
  }
fi

"$ROOT/scripts/build.sh" >/dev/null
rm -rf "$OUT" "$TEMP"
mkdir -p "$OUT" "$TEMP"
(
  cd "$OUT"
  TMPDIR="$TEMP" "$NATIVE_IMAGE" -J-Djava.io.tmpdir="$TEMP" -O2 \
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
