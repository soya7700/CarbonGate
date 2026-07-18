#!/usr/bin/env sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
OUT="$ROOT/build/test-classes"

rm -rf "$OUT"
mkdir -p "$OUT"
"$ROOT/scripts/compile-main.sh" "$OUT"
find "$ROOT/src/test/java" -name '*.java' -print > "$ROOT/build/test-sources.txt"
javac --release 21 -encoding UTF-8 -cp "$OUT" -d "$OUT" @"$ROOT/build/test-sources.txt"
java -ea -cp "$OUT" io.carbongate.AllTests
"$ROOT/scripts/license-check.sh"
