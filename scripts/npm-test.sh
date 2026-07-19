#!/usr/bin/env sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
PACKAGE="$ROOT/adapters/npm/cli"

command -v node >/dev/null 2>&1 || {
  printf '%s\n' 'Node.js 18.17 or newer is required to verify the npm adapter.' >&2
  exit 1
}
node -e 'const [major, minor] = process.versions.node.split(".").map(Number); process.exit(major > 18 || (major === 18 && minor >= 17) ? 0 : 1)' || {
  printf 'Node.js 18.17 or newer is required; found %s.\n' "$(node --version)" >&2
  exit 1
}

NPM_CONFIG_CACHE="$ROOT/build/npm-test-cache" npm --prefix "$PACKAGE" test
printf '%s\n' 'CarbonGate npm adapter tests passed.'
