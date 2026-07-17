#!/usr/bin/env sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
SOURCE="$ROOT/components/providers/approval-policy-provider"
CLASSES="$ROOT/build/approval-policy-provider-classes"
STAGE="$ROOT/build/component-source/approval-policy-provider"
JAR="$ROOT/build/approval-policy-provider.jar"
OUTPUT="$ROOT/build/approval-policy-provider-1.0.0.carbon"

"$ROOT/scripts/build-enterprise.sh" >/dev/null
rm -rf "$CLASSES" "$STAGE"
mkdir -p "$CLASSES" "$STAGE/payload"
find "$SOURCE/src/main/java" -name '*.java' -print > "$ROOT/build/approval-policy-provider-sources.txt"
javac --release 21 -Xlint:all -Werror -encoding UTF-8 -cp "$ROOT/build/carbongate.jar" \
  -d "$CLASSES" @"$ROOT/build/approval-policy-provider-sources.txt"
jar --create --file "$JAR" --main-class io.carbongate.provider.approval.ApprovalPolicyProvider \
  -C "$CLASSES" . -C "$ROOT/build/classes" io/carbongate/json
cp "$SOURCE/manifest.json" "$SOURCE/NOTICE" "$STAGE/"
cp "$ROOT/LICENSE" "$STAGE/LICENSE"
cp "$JAR" "$STAGE/payload/provider.jar"
"$ROOT/build/carbon-enterprise" package "$STAGE" "$OUTPUT" >/dev/null
printf 'Built Approval Policy Provider %s\n' "$OUTPUT"
