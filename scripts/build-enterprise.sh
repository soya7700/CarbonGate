#!/usr/bin/env sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
OUT="$ROOT/build"
CLASSES="$OUT/enterprise-classes"
CORE="$OUT/carbongate.jar"
HOST="$OUT/carbongate-enterprise-host.jar"

test -s "$CORE" || "$ROOT/scripts/build.sh" >/dev/null
rm -rf "$CLASSES"
mkdir -p "$CLASSES"
find "$ROOT/enterprise/src/main/java" -name '*.java' -print > "$OUT/enterprise-sources.txt"
javac --release 21 -Xlint:all -Werror -encoding UTF-8 -cp "$CORE" -d "$CLASSES" @"$OUT/enterprise-sources.txt"
jar --create --file "$HOST" --main-class io.carbongate.enterprise.cli.EnterpriseCli -C "$CLASSES" .

cat > "$OUT/carbon-enterprise" <<'EOF'
#!/usr/bin/env sh
set -eu
HERE=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
exec java -cp "$HERE/carbongate.jar:$HERE/carbongate-enterprise-host.jar" io.carbongate.enterprise.cli.EnterpriseCli "$@"
EOF
chmod +x "$OUT/carbon-enterprise"

cat > "$OUT/carbon-enterprise.cmd" <<'EOF'
@echo off
java -cp "%~dp0carbongate.jar;%~dp0carbongate-enterprise-host.jar" io.carbongate.enterprise.cli.EnterpriseCli %*
EOF

printf 'Built optional Enterprise Component Host %s\n' "$HOST"
