#!/usr/bin/env sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
SOURCE="$ROOT/components/packs/sensitive-data-baseline"
STAGE="$ROOT/build/component-source/sensitive-data-baseline"
OUTPUT="$ROOT/build/sensitive-data-baseline-1.0.0.carbon"

"$ROOT/scripts/build-enterprise.sh" >/dev/null
rm -rf "$STAGE"
mkdir -p "$STAGE"
cp -R "$SOURCE/." "$STAGE/"
cp "$ROOT/LICENSE" "$STAGE/LICENSE"
"$ROOT/build/carbon-enterprise" package "$STAGE" "$OUTPUT" >/dev/null
printf 'Built data-only Pack %s\n' "$OUTPUT"
