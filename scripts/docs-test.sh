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

grep -F 'scripts/install-release.sh | sh -s -- --setup' "$ROOT/README.md" >/dev/null
grep -F 'scripts/install-release.sh | sh -s -- --setup' "$ROOT/README-CN.md" >/dev/null
grep -F "scripts/install-release.ps1'))) -Setup" "$ROOT/README.md" >/dev/null
grep -F "scripts/install-release.ps1'))) -Setup" "$ROOT/README-CN.md" >/dev/null
grep -F 'scripts/install-release.sh | sh -s -- --host codex,claude,openclaw' "$ROOT/README.md" >/dev/null
grep -F 'scripts/install-release.sh | sh -s -- --host codex,claude,openclaw' "$ROOT/README-CN.md" >/dev/null
grep -F 'ROADMAP.md' "$ROOT/README.md" >/dev/null
grep -F 'ROADMAP-CN.md' "$ROOT/README-CN.md" >/dev/null
grep -F '@carbongate/cli' "$ROOT/README.md" >/dev/null
grep -F '@carbongate/cli' "$ROOT/README-CN.md" >/dev/null
grep -F 'docs/npm-adapter.md' "$ROOT/README.md" >/dev/null
grep -F 'docs/npm-adapter.md' "$ROOT/README-CN.md" >/dev/null
grep -F 'npm publish --access public --provenance' "$ROOT/docs/npm-adapter.md" >/dev/null
grep -F '`@carbongate/cli`' "$ROOT/ROADMAP.md" >/dev/null
grep -F '`@carbongate/cli`' "$ROOT/ROADMAP-CN.md" >/dev/null
grep -F '`soya7700/homebrew-tap`' "$ROOT/ROADMAP.md" >/dev/null
grep -F '`soya7700/homebrew-tap`' "$ROOT/ROADMAP-CN.md" >/dev/null
grep -F 'No Java runtime for a prebuilt native local installation' "$ROOT/README.md" >/dev/null
grep -F '预构建原生本地安装不需要 Java 运行时' "$ROOT/README-CN.md" >/dev/null
grep -F 'Java 21 is the product source, bytecode, JVM runtime, and enterprise integration' "$ROOT/README.md" >/dev/null
grep -F 'Java 21 是项目源码、字节码、JVM 运行时和企业集成的统一基线' "$ROOT/README-CN.md" >/dev/null
grep -F 'GraalVM Community 25.1.3 only as the current' "$ROOT/README.md" >/dev/null
grep -F 'GraalVM Community 25.1.3 作为当前开源 Native Image 打包器' "$ROOT/README-CN.md" >/dev/null
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
grep -F 'docs/rule-packs.md' "$ROOT/README.md" >/dev/null
grep -F 'docs/rule-packs.md' "$ROOT/README-CN.md" >/dev/null
grep -F 'docs/sensitive-data-provider.md' "$ROOT/README.md" >/dev/null
grep -F 'docs/sensitive-data-provider.md' "$ROOT/README-CN.md" >/dev/null
grep -F 'docs/container-sandbox.md' "$ROOT/README.md" >/dev/null
grep -F 'docs/container-sandbox.md' "$ROOT/README-CN.md" >/dev/null
grep -F 'docs/enterprise-pipeline.md' "$ROOT/README.md" >/dev/null
grep -F 'docs/enterprise-pipeline.md' "$ROOT/README-CN.md" >/dev/null
grep -F 'docs/approval-provider.md' "$ROOT/README.md" >/dev/null
grep -F 'docs/approval-provider.md' "$ROOT/README-CN.md" >/dev/null
grep -F 'docs/enterprise-audit-provider.md' "$ROOT/README.md" >/dev/null
grep -F 'docs/enterprise-audit-provider.md' "$ROOT/README-CN.md" >/dev/null
grep -F 'docs/component-trust.md' "$ROOT/README.md" >/dev/null
grep -F 'docs/component-trust.md' "$ROOT/README-CN.md" >/dev/null
grep -F '## Product map' "$ROOT/README.md" >/dev/null
grep -F '## 产品地图' "$ROOT/README-CN.md" >/dev/null
grep -F '**CarbonGate Core**' "$ROOT/README.md" >/dev/null
grep -F '**CarbonGate Core**' "$ROOT/README-CN.md" >/dev/null
grep -F '**CarbonGate Skill**' "$ROOT/README.md" >/dev/null
grep -F '**CarbonGate Skill**' "$ROOT/README-CN.md" >/dev/null
grep -F '## 5. Enterprise components' "$ROOT/README.md" >/dev/null
grep -F '## 5. 企业组件' "$ROOT/README-CN.md" >/dev/null
grep -F 'inspect -> authorize -> sandbox' "$ROOT/README.md" >/dev/null
grep -F 'inspect -> authorize -> sandbox' "$ROOT/README-CN.md" >/dev/null

printf 'README installation order and platform commands verified.\n'
