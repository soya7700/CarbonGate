#!/usr/bin/env sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
OUT=${1:?Usage: compile-main.sh OUTPUT_DIRECTORY}
VERSION=$(tr -d '[:space:]' < "$ROOT/VERSION")
GENERATED="$ROOT/build/generated-sources/io/carbongate/BuildInfo.java"
SOURCES="$ROOT/build/main-sources.txt"

printf '%s\n' "$VERSION" | grep -Eq '^[0-9]+\.[0-9]+\.[0-9]+([+-][A-Za-z0-9.-]+)?$' || {
  printf 'VERSION must contain a semantic version; found %s.\n' "$VERSION" >&2
  exit 1
}

mkdir -p "$OUT" "$(dirname -- "$GENERATED")"
{
  printf '%s\n' 'package io.carbongate;' ''
  printf '%s\n' 'public final class BuildInfo {'
  printf '    public static final String VERSION = "%s";\n' "$VERSION"
  printf '%s\n' \
    '    private BuildInfo() {}' \
    '' \
    '    public static boolean nativeImage() {' \
    '        return "runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"));' \
    '    }' \
    '' \
    '    public static String runtimeLabel() {' \
    '        return nativeImage() ? "native" : "Java " + System.getProperty("java.specification.version", "unknown");' \
    '    }' \
    '}'
} > "$GENERATED"

find "$ROOT/src/main/java" -name '*.java' -print > "$SOURCES"
printf '%s\n' "$GENERATED" >> "$SOURCES"
# CARBON_JAVAC_FLAGS is controlled by repository verification scripts.
# shellcheck disable=SC2086
javac --release 21 -encoding UTF-8 ${CARBON_JAVAC_FLAGS:-} -d "$OUT" @"$SOURCES"
