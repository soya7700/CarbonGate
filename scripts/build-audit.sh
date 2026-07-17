#!/usr/bin/env sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
SOURCE="$ROOT/components/providers/enterprise-audit-provider"
CLASSES="$ROOT/build/enterprise-audit-provider-classes"
STAGE="$ROOT/build/component-source/enterprise-audit-provider"
JAR="$ROOT/build/enterprise-audit-provider.jar"
OUTPUT="$ROOT/build/enterprise-audit-provider-1.0.0.carbon"

"$ROOT/scripts/build-enterprise.sh" >/dev/null
rm -rf "$CLASSES" "$STAGE"
mkdir -p "$CLASSES" "$STAGE/payload"
find "$SOURCE/src/main/java" -name '*.java' -print > "$ROOT/build/enterprise-audit-provider-sources.txt"
javac --release 21 -Xlint:all -Werror -encoding UTF-8 -cp "$ROOT/build/carbongate.jar" \
  -d "$CLASSES" @"$ROOT/build/enterprise-audit-provider-sources.txt"
jar --create --file "$JAR" --main-class io.carbongate.provider.audit.EnterpriseAuditProvider \
  -C "$CLASSES" . -C "$ROOT/build/classes" io/carbongate/json
cp "$SOURCE/manifest.json" "$SOURCE/NOTICE" "$STAGE/"
cp "$ROOT/LICENSE" "$STAGE/LICENSE"
cp "$JAR" "$STAGE/payload/provider.jar"
"$ROOT/build/carbon-enterprise" package "$STAGE" "$OUTPUT" >/dev/null
printf 'Built Enterprise Audit Provider %s\n' "$OUTPUT"
