#!/usr/bin/env sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
"$ROOT/scripts/test.sh"
"$ROOT/scripts/build.sh"

VERSION=${1:-0.1.0}
STAGE="$ROOT/build/carbongate-$VERSION"
mkdir -p "$STAGE/bin"
cp "$ROOT/build/carbongate.jar" "$ROOT/build/carbon" "$STAGE/bin/"
cp "$ROOT/LICENSE" "$ROOT/NOTICE" "$ROOT/THIRD_PARTY_NOTICES.md" "$STAGE/"

tar -C "$ROOT/build" -czf "$ROOT/build/carbongate-$VERSION.tar.gz" "carbongate-$VERSION"
printf 'Packaged %s\n' "$ROOT/build/carbongate-$VERSION.tar.gz"
