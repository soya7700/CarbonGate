#!/usr/bin/env sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
"$ROOT/scripts/test.sh"
"$ROOT/scripts/build.sh"

VERSION=${1:-0.2.0}
STAGE="$ROOT/build/carbongate-$VERSION"
mkdir -p "$STAGE/bin" "$STAGE/config" "$STAGE/docs"
cp "$ROOT/build/carbongate.jar" "$ROOT/build/carbon" "$STAGE/bin/"
cp "$ROOT/LICENSE" "$ROOT/NOTICE" "$ROOT/THIRD_PARTY_NOTICES.md" "$STAGE/"
cp "$ROOT/README.md" "$ROOT/README-CN.md" "$ROOT/SECURITY.md" "$STAGE/"
cp "$ROOT/config/carbon.conf.example" "$STAGE/config/"
cp "$ROOT/docs/"*.md "$STAGE/docs/"

tar -C "$ROOT/build" -czf "$ROOT/build/carbongate-$VERSION.tar.gz" "carbongate-$VERSION"
printf 'Packaged %s\n' "$ROOT/build/carbongate-$VERSION.tar.gz"
