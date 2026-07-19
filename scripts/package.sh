#!/usr/bin/env sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
if test "${CARBON_SKIP_TESTS:-0}" != 1; then
  "$ROOT/scripts/test.sh"
fi
"$ROOT/scripts/build.sh"

PROJECT_VERSION=$(tr -d '[:space:]' < "$ROOT/VERSION")
VERSION=${1:-$PROJECT_VERSION}
TAR_NAME=$("$ROOT/scripts/release-asset-name.sh" portable.jvm.tar.asset "$VERSION")
ZIP_NAME=$("$ROOT/scripts/release-asset-name.sh" portable.jvm.zip.asset "$VERSION")
PACKAGE_NAME=${TAR_NAME%.tar.gz}
test "$PACKAGE_NAME" = "${ZIP_NAME%.zip}" || {
  printf '%s\n' 'Portable release asset patterns must use the same package root.' >&2
  exit 1
}
STAGE="$ROOT/build/$PACKAGE_NAME"
mkdir -p "$STAGE/bin" "$STAGE/config" "$STAGE/distribution" "$STAGE/docs" \
  "$STAGE/skills/carbongate/agents"
cp "$ROOT/build/carbongate.jar" "$ROOT/build/carbon" "$ROOT/build/carbon.cmd" "$STAGE/bin/"
cp "$ROOT/LICENSE" "$ROOT/NOTICE" "$ROOT/THIRD_PARTY_NOTICES.md" "$STAGE/"
cp "$ROOT/README.md" "$ROOT/README-CN.md" "$ROOT/ROADMAP.md" \
  "$ROOT/ROADMAP-CN.md" "$ROOT/SECURITY.md" "$STAGE/"
cp "$ROOT/config/carbon.conf.example" "$STAGE/config/"
cp "$ROOT/distribution/release-assets.properties" "$STAGE/distribution/"
cp "$ROOT/docs/"*.md "$STAGE/docs/"
cp "$ROOT/skills/carbongate/SKILL.md" "$STAGE/skills/carbongate/"
cp "$ROOT/skills/carbongate/agents/openai.yaml" "$STAGE/skills/carbongate/agents/"
cp "$ROOT/scripts/install-package.sh" "$STAGE/install.sh"
cp "$ROOT/scripts/install-package.ps1" "$STAGE/install.ps1"
chmod +x "$STAGE/install.sh"

tar -C "$ROOT/build" -czf "$ROOT/build/$TAR_NAME" "$PACKAGE_NAME"
(cd "$ROOT/build" && jar --create --file "$ZIP_NAME" --no-manifest "$PACKAGE_NAME")
printf 'Packaged %s and %s\n' "$ROOT/build/$TAR_NAME" "$ROOT/build/$ZIP_NAME"
