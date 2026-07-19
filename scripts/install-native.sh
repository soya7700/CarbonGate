#!/usr/bin/env sh
set -eu

SCRIPT_DIRECTORY=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PREFIX=${HOME}/.local
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

SOURCE="$SCRIPT_DIRECTORY/bin/carbon"
test -s "$SOURCE" || {
  printf 'Packaged CarbonGate executable is missing: %s\n' "$SOURCE" >&2
  exit 1
}

mkdir -p "$PREFIX/bin"
install -m 0755 "$SOURCE" "$PREFIX/bin/carbon"
LAUNCHER="$PREFIX/bin/carbon"
"$LAUNCHER" version >/dev/null
"$LAUNCHER" config init >/dev/null

if test "$SETUP" = true; then
  INSTALL_CODEX_SKILL=false
  if test -n "$HOSTS"; then
    case ",$HOSTS," in *,codex,*) INSTALL_CODEX_SKILL=true ;; esac
  elif command -v codex >/dev/null 2>&1; then
    INSTALL_CODEX_SKILL=true
  fi
  if test "$INSTALL_CODEX_SKILL" = true && test -s "$SCRIPT_DIRECTORY/skills/carbongate/SKILL.md"; then
    CODEX_STATE=${CODEX_HOME:-"$HOME/.codex"}
    if test ! -e "$CODEX_STATE/skills/carbongate"; then
      mkdir -p "$CODEX_STATE/skills"
      cp -R "$SCRIPT_DIRECTORY/skills/carbongate" "$CODEX_STATE/skills/carbongate"
    fi
  fi
  if test -n "$HOSTS"; then "$LAUNCHER" setup --host "$HOSTS"; else "$LAUNCHER" setup; fi
fi

printf 'Installed CarbonGate to %s\n' "$LAUNCHER"
configure_path() {
  BIN_DIRECTORY=$1
  case ":$PATH:" in
    *":$BIN_DIRECTORY:"*) return ;;
  esac
  if test "$PREFIX" != "$HOME/.local"; then
    printf 'To use CarbonGate now: export PATH="%s:$PATH"\n' "$BIN_DIRECTORY"
    return
  fi
  SHELL_NAME=${SHELL:-}
  case "${SHELL_NAME##*/}" in
    zsh) STARTUP_FILE=$HOME/.zshrc; PATH_LINE='export PATH="$HOME/.local/bin:$PATH"' ;;
    bash) STARTUP_FILE=$HOME/.bashrc; PATH_LINE='export PATH="$HOME/.local/bin:$PATH"' ;;
    fish) STARTUP_FILE=$HOME/.config/fish/conf.d/carbongate.fish; PATH_LINE='fish_add_path -g $HOME/.local/bin' ;;
    *) STARTUP_FILE=$HOME/.profile; PATH_LINE='export PATH="$HOME/.local/bin:$PATH"' ;;
  esac
  if ! test -f "$STARTUP_FILE" || ! grep -F '# CarbonGate CLI' "$STARTUP_FILE" >/dev/null 2>&1; then
    mkdir -p "$(dirname -- "$STARTUP_FILE")"
    {
      printf '\n# CarbonGate CLI\n'
      printf '%s\n' "$PATH_LINE"
    } >> "$STARTUP_FILE"
    printf 'Added CarbonGate to PATH for new terminals via %s\n' "$STARTUP_FILE"
  fi
  printf 'To use CarbonGate now: export PATH="%s:$PATH"\n' "$BIN_DIRECTORY"
}

configure_path "$PREFIX/bin"
