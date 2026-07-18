#!/usr/bin/env sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
LINT_OUT=$(mktemp -d "${TMPDIR:-/tmp}/carbongate-lint.XXXXXX")
trap 'rm -rf "$LINT_OUT"' EXIT HUP INT TERM

java_spec=$(java -XshowSettings:properties -version 2>&1 | \
  sed -n 's/^[[:space:]]*java.specification.version = //p' | head -n 1)
test -n "$java_spec" || {
  printf 'Unable to determine Java specification version.\n' >&2
  exit 1
}
test "$java_spec" -ge 21 || {
  printf 'CarbonGate requires JDK 21 or newer; found Java %s.\n' "$java_spec" >&2
  exit 1
}

mkdir -p "$LINT_OUT/classes"
CARBON_JAVAC_FLAGS='-Xlint:all -Werror' "$ROOT/scripts/compile-main.sh" "$LINT_OUT/classes"
find "$ROOT/src/test/java" -name '*.java' -print > "$LINT_OUT/test-sources.txt"
javac --release 21 -Xlint:all -Werror -encoding UTF-8 -cp "$LINT_OUT/classes" \
  -d "$LINT_OUT/classes" @"$LINT_OUT/test-sources.txt"

"$ROOT/scripts/test.sh"
"$ROOT/scripts/docs-test.sh"
"$ROOT/scripts/build.sh"
"$ROOT/scripts/enterprise-test.sh"
"$ROOT/scripts/provider-test.sh"
"$ROOT/scripts/sandbox-test.sh"
"$ROOT/scripts/approval-test.sh"
"$ROOT/scripts/audit-provider-test.sh"
"$ROOT/scripts/functional-test.sh"
"$ROOT/scripts/package-test.sh"
"$ROOT/scripts/lightweight-budget.sh"

printf 'Complete CarbonGate verification passed on Java %s.\n' "$java_spec"
