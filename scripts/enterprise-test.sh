#!/usr/bin/env sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
OUT="$ROOT/build"
TEST_CLASSES="$OUT/enterprise-test-classes"

"$ROOT/scripts/build-enterprise.sh" >/dev/null
rm -rf "$TEST_CLASSES"
mkdir -p "$TEST_CLASSES"
find "$ROOT/enterprise/src/test/java" -name '*.java' -print > "$OUT/enterprise-test-sources.txt"
javac --release 21 -Xlint:all -Werror -encoding UTF-8 \
  -cp "$OUT/carbongate.jar:$OUT/enterprise-classes" \
  -d "$TEST_CLASSES" @"$OUT/enterprise-test-sources.txt"
java -ea -cp "$OUT/carbongate.jar:$OUT/enterprise-classes:$TEST_CLASSES" \
  io.carbongate.enterprise.EnterpriseAllTests

"$OUT/carbon-enterprise" version | grep -F 'protocol v1, Java 21' >/dev/null
"$ROOT/scripts/build-pack.sh" >/dev/null
test -s "$OUT/sensitive-data-baseline-1.0.0.carbon"
printf 'CarbonGate Enterprise Component Host verification passed.\n'
