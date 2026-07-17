#!/usr/bin/env sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
SOURCE="$ROOT/components/providers/approval-policy-provider"
TEST_CLASSES="$ROOT/build/approval-policy-provider-test-classes"
HOME_DIR=$(mktemp -d "${TMPDIR:-/tmp}/carbongate-approval.XXXXXX")
trap 'rm -rf "$HOME_DIR"' EXIT HUP INT TERM

"$ROOT/scripts/build-approval.sh" >/dev/null
rm -rf "$TEST_CLASSES"
mkdir -p "$TEST_CLASSES"
find "$SOURCE/src/test/java" -name '*.java' -print > "$ROOT/build/approval-policy-provider-test-sources.txt"
javac --release 21 -Xlint:all -Werror -encoding UTF-8 \
  -cp "$ROOT/build/carbongate.jar:$ROOT/build/approval-policy-provider-classes" \
  -d "$TEST_CLASSES" @"$ROOT/build/approval-policy-provider-test-sources.txt"
java -ea -cp "$ROOT/build/carbongate.jar:$ROOT/build/approval-policy-provider-classes:$TEST_CLASSES" \
  io.carbongate.provider.approval.ApprovalPolicyProviderTest

CARBON_HOME="$HOME_DIR" "$ROOT/build/carbon-enterprise" install "$ROOT/build/approval-policy-provider-1.0.0.carbon" >/dev/null
CARBON_HOME="$HOME_DIR" "$ROOT/build/carbon-enterprise" enable approval-policy-provider 1.0.0 >/dev/null
CARBON_HOME="$HOME_DIR" "$ROOT/build/carbon-enterprise" guard '{"action":"shell","risk":"high"}' \
  | grep -F '"decision":"ask"' >/dev/null
CARBON_HOME="$HOME_DIR" "$ROOT/build/carbon-enterprise" guard '{"action":"read","risk":"critical"}' \
  | grep -F '"decision":"deny"' >/dev/null
printf 'Approval Policy Provider end-to-end verification passed.\n'
