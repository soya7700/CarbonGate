#!/usr/bin/env sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
PREFIX="${HOME}/.local"

while test "$#" -gt 0; do
  case "$1" in
    --prefix)
      test "$#" -ge 2 || { printf '%s\n' '--prefix requires a path' >&2; exit 2; }
      PREFIX=$2
      shift 2
      ;;
    *)
      printf 'Unknown option: %s\n' "$1" >&2
      exit 2
      ;;
  esac
done

"$ROOT/scripts/build.sh"
mkdir -p "$PREFIX/lib/carbongate" "$PREFIX/bin"
install -m 0644 "$ROOT/build/carbongate.jar" "$PREFIX/lib/carbongate/carbongate.jar"

LAUNCHER="$PREFIX/bin/carbon"
{
  printf '%s\n' '#!/usr/bin/env sh' 'set -eu'
  printf 'exec java -jar "%s" "$@"\n' "$PREFIX/lib/carbongate/carbongate.jar"
} > "$LAUNCHER"
chmod +x "$LAUNCHER"

"$LAUNCHER" config init >/dev/null

printf 'Installed CarbonGate to %s\n' "$LAUNCHER"
case ":$PATH:" in
  *":$PREFIX/bin:"*) ;;
  *) printf 'Add %s/bin to PATH.\n' "$PREFIX" ;;
esac
