#!/usr/bin/env sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
if test "${CARBON_SKIP_TESTS:-0}" != 1; then
  "$ROOT/scripts/test.sh"
fi
"$ROOT/scripts/build.sh"

PROJECT_VERSION=$(tr -d '[:space:]' < "$ROOT/VERSION")
VERSION=${1:-$PROJECT_VERSION}
STAGE="$ROOT/build/carbongate-$VERSION"
mkdir -p "$STAGE/bin" "$STAGE/config" "$STAGE/docs" "$STAGE/skills/carbongate/agents"
cp "$ROOT/build/carbongate.jar" "$ROOT/build/carbon" "$ROOT/build/carbon.cmd" "$STAGE/bin/"
cp "$ROOT/LICENSE" "$ROOT/NOTICE" "$ROOT/THIRD_PARTY_NOTICES.md" "$STAGE/"
cp "$ROOT/README.md" "$ROOT/README-CN.md" "$ROOT/SECURITY.md" "$STAGE/"
cp "$ROOT/config/carbon.conf.example" "$STAGE/config/"
cp "$ROOT/docs/"*.md "$STAGE/docs/"
cp "$ROOT/skills/carbongate/SKILL.md" "$STAGE/skills/carbongate/"
cp "$ROOT/skills/carbongate/agents/openai.yaml" "$STAGE/skills/carbongate/agents/"
cp "$ROOT/scripts/install-package.sh" "$STAGE/install.sh"
cp "$ROOT/scripts/install-package.ps1" "$STAGE/install.ps1"
chmod +x "$STAGE/install.sh"

tar -C "$ROOT/build" -czf "$ROOT/build/carbongate-$VERSION.tar.gz" "carbongate-$VERSION"
(cd "$ROOT/build" && jar --create --file "carbongate-$VERSION.zip" --no-manifest "carbongate-$VERSION")
printf 'Packaged %s and %s\n' "$ROOT/build/carbongate-$VERSION.tar.gz" "$ROOT/build/carbongate-$VERSION.zip"
