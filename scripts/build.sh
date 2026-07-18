#!/usr/bin/env sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
OUT="$ROOT/build"
CLASSES="$OUT/classes"

rm -rf "$OUT"
mkdir -p "$CLASSES"
"$ROOT/scripts/compile-main.sh" "$CLASSES"
jar --create --file "$OUT/carbongate.jar" --main-class io.carbongate.cli.CarbonCli -C "$CLASSES" .

cat > "$OUT/carbon" <<'EOF'
#!/usr/bin/env sh
set -eu
HERE=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
exec java -jar "$HERE/carbongate.jar" "$@"
EOF
chmod +x "$OUT/carbon"

cat > "$OUT/carbon.cmd" <<'EOF'
@echo off
java -jar "%~dp0carbongate.jar" %*
EOF
printf 'Built %s\n' "$OUT/carbon"
