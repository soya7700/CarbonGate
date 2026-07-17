#!/usr/bin/env sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
SOURCE="$ROOT/components/providers/sensitive-data-provider"
TEST_CLASSES="$ROOT/build/sensitive-data-provider-test-classes"
HOME_DIR=$(mktemp -d "${TMPDIR:-/tmp}/carbongate-provider.XXXXXX")
trap 'rm -rf "$HOME_DIR"' EXIT HUP INT TERM

"$ROOT/scripts/build-pack.sh" >/dev/null
"$ROOT/scripts/build-provider.sh" >/dev/null
rm -rf "$TEST_CLASSES"
mkdir -p "$TEST_CLASSES"
find "$SOURCE/src/test/java" -name '*.java' -print > "$ROOT/build/sensitive-data-provider-test-sources.txt"
javac --release 21 -Xlint:all -Werror -encoding UTF-8 \
  -cp "$ROOT/build/carbongate.jar:$ROOT/build/sensitive-data-provider-classes" \
  -d "$TEST_CLASSES" @"$ROOT/build/sensitive-data-provider-test-sources.txt"
java -ea -cp "$ROOT/build/carbongate.jar:$ROOT/build/sensitive-data-provider-classes:$TEST_CLASSES" \
  io.carbongate.provider.dlp.SensitiveDataProviderTest

CARBON_HOME="$HOME_DIR" "$ROOT/build/carbon-enterprise" install "$ROOT/build/sensitive-data-baseline-1.0.0.carbon" >/dev/null
CARBON_HOME="$HOME_DIR" "$ROOT/build/carbon-enterprise" enable sensitive-data-baseline 1.0.0 >/dev/null
CARBON_HOME="$HOME_DIR" "$ROOT/build/carbon-enterprise" install "$ROOT/build/sensitive-data-provider-1.0.0.carbon" >/dev/null
CARBON_HOME="$HOME_DIR" "$ROOT/build/carbon-enterprise" enable sensitive-data-provider 1.0.0 >/dev/null
result=$(CARBON_HOME="$HOME_DIR" "$ROOT/build/carbon-enterprise" invoke sensitive-data-provider inspect \
  '{"text":"身份证 11010519491231002X，会员余额 900"}')
printf '%s' "$result" | grep -F '"decision":"block"' >/dev/null
printf '%s' "$result" | grep -F 'personal.id-cn' >/dev/null
if printf '%s' "$result" | grep -F '11010519491231002X' >/dev/null; then
  printf 'Sensitive content leaked in Provider response.\n' >&2
  exit 1
fi
printf 'Sensitive Data Provider end-to-end verification passed.\n'
