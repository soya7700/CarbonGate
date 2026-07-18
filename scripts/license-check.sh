#!/usr/bin/env sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)

for required in LICENSE NOTICE THIRD_PARTY_NOTICES.md docs/dependency-policy.md \
  licenses/GPL-2.0-with-Classpath-Exception.txt; do
  test -s "$ROOT/$required" || {
    printf 'Missing license artifact: %s\n' "$required" >&2
    exit 1
  }
done

if find "$ROOT/src" "$ROOT/enterprise/src" "$ROOT/components" \( -name '*.jar' -o -name '*.class' \) | grep -q .; then
  printf 'Compiled or bundled binary found under src; review third-party provenance.\n' >&2
  exit 1
fi

imports=$(find "$ROOT/src" "$ROOT/enterprise/src" "$ROOT/components" -name '*.java' -exec sed -n 's/^import \([^;]*\);/\1/p' {} \; | \
  grep -Ev '^(java\.|javax\.|jdk\.|com\.sun\.net\.httpserver\.|io\.carbongate\.)' || true)
if test -n "$imports"; then
  printf 'Undeclared non-JDK imports detected:\n%s\n' "$imports" >&2
  exit 1
fi

if test -d "$ROOT/.github/workflows"; then
  workflow_uses=$(grep -RhoE 'uses:[[:space:]]+[^[:space:]]+' "$ROOT/.github/workflows" | \
    sed 's/^uses:[[:space:]]*//' || true)
  unapproved=$(printf '%s\n' "$workflow_uses" | grep -Ev \
    '^(actions/checkout@34e114876b0b11c390a56381ad16ebd13914f8d5|actions/setup-java@c1e323688fd81a25caa38c78aa6df2d33d3e20d9|graalvm/setup-graalvm@0def53c0fd8534bc13416c9469f5be45265824fd)$' || true)
  if test -n "$unapproved"; then
    printf 'Unapproved or unpinned GitHub Actions detected:\n%s\n' "$unapproved" >&2
    exit 1
  fi
fi

printf 'License check passed: no undeclared Java, binary, or CI dependencies.\n'
