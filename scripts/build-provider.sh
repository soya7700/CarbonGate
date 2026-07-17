#!/usr/bin/env sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
SOURCE="$ROOT/components/providers/sensitive-data-provider"
CLASSES="$ROOT/build/sensitive-data-provider-classes"
STAGE="$ROOT/build/component-source/sensitive-data-provider"
JAR="$ROOT/build/sensitive-data-provider.jar"
OUTPUT="$ROOT/build/sensitive-data-provider-1.0.0.carbon"

"$ROOT/scripts/build-enterprise.sh" >/dev/null
rm -rf "$CLASSES" "$STAGE"
mkdir -p "$CLASSES" "$STAGE/payload"
find "$SOURCE/src/main/java" -name '*.java' -print > "$ROOT/build/sensitive-data-provider-sources.txt"
javac --release 21 -Xlint:all -Werror -encoding UTF-8 -cp "$ROOT/build/carbongate.jar" \
  -d "$CLASSES" @"$ROOT/build/sensitive-data-provider-sources.txt"
jar --create --file "$JAR" --main-class io.carbongate.provider.dlp.SensitiveDataProvider \
  -C "$CLASSES" . -C "$ROOT/build/classes" io/carbongate/json
cp "$SOURCE/manifest.json" "$SOURCE/NOTICE" "$STAGE/"
cp "$ROOT/LICENSE" "$STAGE/LICENSE"
cp "$JAR" "$STAGE/payload/provider.jar"
"$ROOT/build/carbon-enterprise" package "$STAGE" "$OUTPUT" >/dev/null
printf 'Built Sensitive Data Provider %s\n' "$OUTPUT"
