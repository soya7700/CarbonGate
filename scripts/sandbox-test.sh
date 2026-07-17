#!/usr/bin/env sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
SOURCE="$ROOT/components/sandboxes/container-sandbox"
TEST_CLASSES="$ROOT/build/container-sandbox-test-classes"
HOME_DIR=$(mktemp -d "${TMPDIR:-/tmp}/carbongate-sandbox.XXXXXX")
trap 'rm -rf "$HOME_DIR"' EXIT HUP INT TERM

"$ROOT/scripts/build-sandbox.sh" >/dev/null
rm -rf "$TEST_CLASSES"
mkdir -p "$TEST_CLASSES"
find "$SOURCE/src/test/java" -name '*.java' -print > "$ROOT/build/container-sandbox-test-sources.txt"
javac --release 21 -Xlint:all -Werror -encoding UTF-8 \
  -cp "$ROOT/build/carbongate.jar:$ROOT/build/container-sandbox-classes" \
  -d "$TEST_CLASSES" @"$ROOT/build/container-sandbox-test-sources.txt"
java -ea -cp "$ROOT/build/carbongate.jar:$ROOT/build/container-sandbox-classes:$TEST_CLASSES" \
  io.carbongate.sandbox.container.ContainerSandboxProviderTest

CARBON_HOME="$HOME_DIR" "$ROOT/build/carbon-enterprise" install "$ROOT/build/container-sandbox-1.0.0.carbon" >/dev/null
CARBON_HOME="$HOME_DIR" "$ROOT/build/carbon-enterprise" list | grep -F '"kind":"sandbox"' >/dev/null
printf 'Container Sandbox Provider package verification passed.\n'
