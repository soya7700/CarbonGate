#!/usr/bin/env sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
OUT="$ROOT/build"
CLASSES="$OUT/classes"

rm -rf "$OUT"
mkdir -p "$CLASSES"
find "$ROOT/src/main/java" -name '*.java' -print > "$OUT/sources.txt"
javac --release 21 -encoding UTF-8 -d "$CLASSES" @"$OUT/sources.txt"
jar --create --file "$OUT/carbongate.jar" --main-class io.carbongate.cli.CarbonCli -C "$CLASSES" .

cat > "$OUT/carbon" <<'EOF'
#!/usr/bin/env sh
set -eu
HERE=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
exec java -jar "$HERE/carbongate.jar" "$@"
EOF
chmod +x "$OUT/carbon"
printf 'Built %s\n' "$OUT/carbon"
