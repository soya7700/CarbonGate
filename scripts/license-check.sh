#!/usr/bin/env sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)

for required in LICENSE NOTICE THIRD_PARTY_NOTICES.md docs/dependency-policy.md; do
  test -s "$ROOT/$required" || {
    printf 'Missing license artifact: %s\n' "$required" >&2
    exit 1
  }
done

if find "$ROOT/src" -name '*.jar' -o -name '*.class' | grep -q .; then
  printf 'Compiled or bundled binary found under src; review third-party provenance.\n' >&2
  exit 1
fi

imports=$(find "$ROOT/src" -name '*.java' -exec sed -n 's/^import \([^;]*\);/\1/p' {} \; | \
  grep -Ev '^(java\.|javax\.|jdk\.|com\.sun\.net\.httpserver\.|io\.carbongate\.)' || true)
if test -n "$imports"; then
  printf 'Undeclared non-JDK imports detected:\n%s\n' "$imports" >&2
  exit 1
fi

printf 'License check passed: no third-party Java imports or bundled source binaries.\n'
