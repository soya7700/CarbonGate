#!/usr/bin/env sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
PREFIX="${HOME}/.local"
SETUP=false
HOSTS=

while test "$#" -gt 0; do
  case "$1" in
    --prefix)
      test "$#" -ge 2 || { printf '%s\n' '--prefix requires a path' >&2; exit 2; }
      PREFIX=$2
      shift 2
      ;;
    --setup)
      SETUP=true
      shift
      ;;
    --host)
      test "$#" -ge 2 || { printf '%s\n' '--host requires a comma-separated host list' >&2; exit 2; }
      SETUP=true
      HOSTS=$2
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

if test "$SETUP" = true; then
  INSTALL_CODEX_SKILL=false
  if test -n "$HOSTS"; then
    case ",$HOSTS," in *,codex,*) INSTALL_CODEX_SKILL=true ;; esac
  elif command -v codex >/dev/null 2>&1; then
    INSTALL_CODEX_SKILL=true
  fi
  if test "$INSTALL_CODEX_SKILL" = true && test -s "$ROOT/skills/carbongate/SKILL.md"; then
    CODEX_STATE=${CODEX_HOME:-"$HOME/.codex"}
    if test ! -e "$CODEX_STATE/skills/carbongate"; then
      mkdir -p "$CODEX_STATE/skills"
      cp -R "$ROOT/skills/carbongate" "$CODEX_STATE/skills/carbongate"
    fi
  fi
  if test -n "$HOSTS"; then
    "$LAUNCHER" setup --host "$HOSTS"
  else
    "$LAUNCHER" setup
  fi
fi

printf 'Installed CarbonGate to %s\n' "$LAUNCHER"
case ":$PATH:" in
  *":$PREFIX/bin:"*) ;;
  *) printf 'Add %s/bin to PATH.\n' "$PREFIX" ;;
esac
