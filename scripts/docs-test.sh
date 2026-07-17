#!/usr/bin/env sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)

english_recommended=$(grep -n '^## 1\. Recommended: one-command installation$' "$ROOT/README.md" | cut -d: -f1)
english_source=$(grep -n '^## 2\. Alternative: install from source$' "$ROOT/README.md" | cut -d: -f1)
chinese_recommended=$(grep -n '^## 1\. 推荐：一键安装$' "$ROOT/README-CN.md" | cut -d: -f1)
chinese_source=$(grep -n '^## 2\. 次选：从源码安装$' "$ROOT/README-CN.md" | cut -d: -f1)

test -n "$english_recommended"
test -n "$english_source"
test -n "$chinese_recommended"
test -n "$chinese_source"
test "$english_recommended" -lt "$english_source"
test "$chinese_recommended" -lt "$chinese_source"

grep -F './carbongate-VERSION/install.sh --setup' "$ROOT/README.md" >/dev/null
grep -F '.\carbongate-VERSION\install.ps1 -Setup' "$ROOT/README.md" >/dev/null
grep -F './carbongate-VERSION/install.sh --setup' "$ROOT/README-CN.md" >/dev/null
grep -F '.\carbongate-VERSION\install.ps1 -Setup' "$ROOT/README-CN.md" >/dev/null
grep -F '.\carbongate-VERSION\install.ps1 -Hosts "codex,claude,openclaw"' "$ROOT/README.md" >/dev/null
grep -F '.\carbongate-VERSION\install.ps1 -Hosts "codex,claude,openclaw"' "$ROOT/README-CN.md" >/dev/null
grep -F 'JDK 21-targeted' "$ROOT/README.md" >/dev/null
grep -F 'JDK 21 为目标' "$ROOT/README-CN.md" >/dev/null
grep -F 'carbon mcp profile add filesystem' "$ROOT/README.md" >/dev/null
grep -F 'carbon mcp profile add filesystem' "$ROOT/README-CN.md" >/dev/null
grep -F '$CARBON_HOME/mcp/profiles.json' "$ROOT/README.md" >/dev/null
grep -F '$CARBON_HOME/mcp/profiles.json' "$ROOT/README-CN.md" >/dev/null
grep -F 'docs/architecture.md' "$ROOT/README.md" >/dev/null
grep -F 'docs/architecture.md' "$ROOT/README-CN.md" >/dev/null
grep -F 'carbon protect /absolute/project/path' "$ROOT/README.md" >/dev/null
grep -F 'carbon protect /absolute/project/path' "$ROOT/README-CN.md" >/dev/null
grep -F 'name: carbongate' "$ROOT/skills/carbongate/SKILL.md" >/dev/null
grep -F 'docs/enterprise-components.md' "$ROOT/README.md" >/dev/null
grep -F 'docs/enterprise-components.md' "$ROOT/README-CN.md" >/dev/null

printf 'README installation order and platform commands verified.\n'
