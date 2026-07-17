#!/usr/bin/env sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
SOURCE="$ROOT/components/sandboxes/container-sandbox"
CLASSES="$ROOT/build/container-sandbox-classes"
STAGE="$ROOT/build/component-source/container-sandbox"
JAR="$ROOT/build/container-sandbox.jar"
OUTPUT="$ROOT/build/container-sandbox-1.0.0.carbon"

"$ROOT/scripts/build-enterprise.sh" >/dev/null
rm -rf "$CLASSES" "$STAGE"
mkdir -p "$CLASSES" "$STAGE/payload"
find "$SOURCE/src/main/java" -name '*.java' -print > "$ROOT/build/container-sandbox-sources.txt"
javac --release 21 -Xlint:all -Werror -encoding UTF-8 -cp "$ROOT/build/carbongate.jar" \
  -d "$CLASSES" @"$ROOT/build/container-sandbox-sources.txt"
jar --create --file "$JAR" --main-class io.carbongate.sandbox.container.ContainerSandboxProvider \
  -C "$CLASSES" . -C "$ROOT/build/classes" io/carbongate/json
cp "$SOURCE/manifest.json" "$SOURCE/NOTICE" "$STAGE/"
cp "$ROOT/LICENSE" "$STAGE/LICENSE"
cp "$JAR" "$STAGE/payload/sandbox.jar"
"$ROOT/build/carbon-enterprise" package "$STAGE" "$OUTPUT" >/dev/null
printf 'Built Container Sandbox Provider %s\n' "$OUTPUT"
