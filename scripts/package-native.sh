#!/usr/bin/env sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
VERSION=${1:-$(tr -d '[:space:]' < "$ROOT/VERSION")}

if test -n "${CARBON_PLATFORM:-}"; then
  PLATFORM=$CARBON_PLATFORM
else
  case "$(uname -s):$(uname -m)" in
    Darwin:arm64) PLATFORM=darwin-arm64 ;;
    Darwin:x86_64) PLATFORM=darwin-x64 ;;
    Linux:x86_64) PLATFORM=linux-x64 ;;
    Linux:aarch64|Linux:arm64) PLATFORM=linux-arm64 ;;
    MINGW*:x86_64|MSYS*:x86_64|CYGWIN*:x86_64) PLATFORM=windows-x64 ;;
    *) printf 'Unsupported native release platform: %s/%s\n' "$(uname -s)" "$(uname -m)" >&2; exit 1 ;;
  esac
fi

case "$PLATFORM" in
  windows-*) BINARY="$ROOT/build/native/carbon.exe" ;;
  *) BINARY="$ROOT/build/native/carbon" ;;
esac
test -s "$BINARY" || "$ROOT/scripts/build-native.sh" >/dev/null

NAME="carbongate-$VERSION-$PLATFORM"
STAGE="$ROOT/build/release/$NAME"
ASSETS="$ROOT/build/release-assets"
rm -rf "$STAGE"
mkdir -p "$STAGE/bin" "$STAGE/config" "$STAGE/docs" "$STAGE/licenses" \
  "$STAGE/skills/carbongate/agents" "$ASSETS"
cp "$BINARY" "$STAGE/bin/"
cp "$ROOT/LICENSE" "$ROOT/NOTICE" "$ROOT/THIRD_PARTY_NOTICES.md" "$STAGE/"
cp "$ROOT/licenses/GPL-2.0-with-Classpath-Exception.txt" "$STAGE/licenses/"
cp "$ROOT/README.md" "$ROOT/README-CN.md" "$ROOT/SECURITY.md" "$STAGE/"
cp "$ROOT/config/carbon.conf.example" "$STAGE/config/"
cp "$ROOT/docs/"*.md "$STAGE/docs/"
cp "$ROOT/skills/carbongate/SKILL.md" "$STAGE/skills/carbongate/"
cp "$ROOT/skills/carbongate/agents/openai.yaml" "$STAGE/skills/carbongate/agents/"

case "$PLATFORM" in
  windows-*)
    cp "$ROOT/scripts/install-native.ps1" "$STAGE/install.ps1"
    (cd "$ROOT/build/release" && jar --create --file "$ASSETS/$NAME.zip" --no-manifest "$NAME")
    ASSET="$ASSETS/$NAME.zip"
    ;;
  *)
    cp "$ROOT/scripts/install-native.sh" "$STAGE/install.sh"
    chmod +x "$STAGE/install.sh" "$STAGE/bin/carbon"
    tar -C "$ROOT/build/release" -czf "$ASSETS/$NAME.tar.gz" "$NAME"
    ASSET="$ASSETS/$NAME.tar.gz"
    ;;
esac

if command -v shasum >/dev/null 2>&1; then
  (cd "$ASSETS" && shasum -a 256 "$(basename -- "$ASSET")") > "$ASSET.sha256"
else
  (cd "$ASSETS" && sha256sum "$(basename -- "$ASSET")") > "$ASSET.sha256"
fi
printf '%s\n' "$ASSET"
