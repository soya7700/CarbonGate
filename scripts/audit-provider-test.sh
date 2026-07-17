#!/usr/bin/env sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
SOURCE="$ROOT/components/providers/enterprise-audit-provider"
TEST_CLASSES="$ROOT/build/enterprise-audit-provider-test-classes"
HOME_DIR=$(mktemp -d "${TMPDIR:-/tmp}/carbongate-audit-provider.XXXXXX")
trap 'rm -rf "$HOME_DIR"' EXIT HUP INT TERM

"$ROOT/scripts/build-audit.sh" >/dev/null
rm -rf "$TEST_CLASSES"
mkdir -p "$TEST_CLASSES"
find "$SOURCE/src/test/java" -name '*.java' -print > "$ROOT/build/enterprise-audit-provider-test-sources.txt"
javac --release 21 -Xlint:all -Werror -encoding UTF-8 \
  -cp "$ROOT/build/carbongate.jar:$ROOT/build/enterprise-audit-provider-classes" \
  -d "$TEST_CLASSES" @"$ROOT/build/enterprise-audit-provider-test-sources.txt"
java -ea -cp "$ROOT/build/carbongate.jar:$ROOT/build/enterprise-audit-provider-classes:$TEST_CLASSES" \
  io.carbongate.provider.audit.EnterpriseAuditProviderTest

CARBON_HOME="$HOME_DIR" "$ROOT/build/carbon-enterprise" install "$ROOT/build/enterprise-audit-provider-1.0.0.carbon" >/dev/null
CARBON_HOME="$HOME_DIR" "$ROOT/build/carbon-enterprise" enable enterprise-audit-provider 1.0.0 >/dev/null
CARBON_HOME="$HOME_DIR" "$ROOT/build/carbon-enterprise" guard '{"action":"read","risk":"low"}' \
  | grep -F 'enterprise-audit-provider' >/dev/null
find "$HOME_DIR/enterprise/components/enterprise-audit-provider/1.0.0/state" -name 'audit-*.jsonl' -size +0 | grep -q .
printf 'Enterprise Audit Provider end-to-end verification passed.\n'
