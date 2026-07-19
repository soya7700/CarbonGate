# CarbonGate

面向 AI Agent 与 MCP Server 的本地优先、零信任安全网关。

[English](README.md)

CarbonGate 在命令或 MCP Tool Call 执行前进行评估，限制文件和网络访问，
脱敏密钥，并对高风险操作要求明确授权。默认安装保持轻量；企业能力全部作为
独立组件按需安装。

> [!IMPORTANT]
> CarbonGate 只保护经过 CLI、Java API、HTTP Gateway 或 MCP Proxy 的流量，
> 不会自动拦截宿主内置工具。

## 产品地图

| 产品 | 职责 | 默认安装 |
|---|---|---:|
| **CarbonGate Core** | 命令/MCP 策略、授权、精简日志、CLI、Java 与 HTTP API | 是 |
| **CarbonGate Skill** | 在 Codex 中通过自然语言安装、查询、保护和控制 | Codex 接入时 |
| **npm Adapter** | 用于校验原生 Release 的显式 Node.js 启动适配器 | 否 |
| **Enterprise Component Host** | 组件生命周期、进程隔离、健康检查与 Guard Pipeline | 否 |
| **Pack** | 纯声明式规则，永不执行代码 | 否 |
| **Provider** | DLP 检查、审批、审计或企业系统集成 | 否 |
| **Sandbox** | 基于容器的命令隔离 | 否 |

企业代码不会进入 Core JAR，详见
[轻量化架构边界](docs/architecture.md)和[迭代路线图](ROADMAP-CN.md)。

```text
本地 Agent 路径
  Agent / MCP 宿主 -> CarbonGate Skill -> Core -> 受保护的 MCP 路由

企业路径
  应用 -> Enterprise Guard Pipeline
          inspect -> authorize -> sandbox（仅 allow 后）-> audit
             |           |               |                |
           Pack       Provider        Sandbox          Provider
```

## 选择使用路径

| 目标 | 从这里开始 |
|---|---|
| 为 Codex、Claude Code、OpenClaw 或其他本地 CLI 安装 | [推荐一键安装](#1-推荐一键安装) |
| 保护 MCP Server | [本地 Agent 与 MCP 接入](#3-本地-agent-与-mcp-接入) |
| 查询拦截、授权、规则或切换级别 | [Core 控制与配置](#4-core-控制与配置) |
| 使用 Pack、DLP、审批、审计或 Sandbox | [企业组件](#5-企业组件) |
| 集成 Java 21 服务 | [Java 项目集成](#6-java-21-项目集成) |
| 开发或修改 CarbonGate | [从源码安装](#2-次选从源码安装) |

## 环境要求

- 预构建原生本地安装不需要 Java 运行时
- macOS/Linux 引导安装需要 `curl`、`tar` 以及 `shasum` 或 `sha256sum`
- 仅可选 npm 适配器需要 Node.js 18.17+
- 只有源码、JVM 或企业组件构建需要 JDK 21（`java`、`javac`、`jar`）和 Git
- macOS、Linux 或 Windows PowerShell 5.1+

Java 21 是项目源码、字节码、JVM 运行时和企业集成的统一基线。Release 自动化仅
使用 GraalVM Community 25.1.3 作为当前开源 Native Image 打包器；生成的原生 CLI
不依赖 JDK 运行时，也不会改变 CarbonGate 的 Java 21 兼容基线。

Core 和第一方组件不依赖第三方源码或运行时库。Container Sandbox 可以调用
用户自行安装的 Docker 或 Podman CLI；CarbonGate 不下载也不分发它们。

## 1. 推荐：一键安装

引导安装器会从
[GitHub 最新 Release](https://github.com/soya7700/CarbonGate/releases/latest)
选择与平台匹配的原生包，校验公开的 SHA-256 后运行包内安装器。不要求 Java，
也不会启动后台服务。

### macOS 和 Linux

Apple Silicon macOS 和 x64 Linux：

```bash
curl -fsSL https://raw.githubusercontent.com/soya7700/CarbonGate/main/scripts/install-release.sh | sh -s -- --setup
```

只配置指定宿主：

```bash
curl -fsSL https://raw.githubusercontent.com/soya7700/CarbonGate/main/scripts/install-release.sh | sh -s -- --host codex,claude,openclaw
```

安装器会把 `~/.local/bin` 写入检测到的 Shell 启动文件，使新终端自动可用。
子进程不能修改当前终端的环境，因此当前终端需执行一次：

```bash
export PATH="$HOME/.local/bin:$PATH"
```

### Windows

在 x64 Windows 打开 PowerShell：

```powershell
& ([scriptblock]::Create((irm 'https://raw.githubusercontent.com/soya7700/CarbonGate/main/scripts/install-release.ps1'))) -Setup
```

只配置指定宿主：

```powershell
& ([scriptblock]::Create((irm 'https://raw.githubusercontent.com/soya7700/CarbonGate/main/scripts/install-release.ps1'))) -Hosts "codex,claude,openclaw"
```

在当前终端立即使用：

```powershell
$env:Path = "$env:LOCALAPPDATA\CarbonGate\bin;$env:Path"
```

### npm 适配器

可选的 @carbongate/cli 只会在存在匹配 GitHub Release 后由维护者手动发布。
发布完成后，Node.js 18.17+ 用户可在不安装 Java 的情况下使用同一条校验 Release
安装路径：

```bash
npx @carbongate/cli install
npx @carbongate/cli setup
npx @carbongate/cli setup --host codex,claude,openclaw
```

安装 npm 包本身不会下载任何内容；显式执行 install 才会下载匹配的原生 Release、
校验 SHA-256，并调用其内置安装器，且不修改 Agent 宿主；setup 才额外配置宿主。
详见 [npm 适配器契约](docs/npm-adapter.md)。

### 安装器行为

引导安装器只通过 HTTPS 下载清单、选中的 CarbonGate 安装包和对应校验文件。
包内安装器随后会：

- 安装前验证原生可执行文件；
- 保留已有 `carbon.conf`；
- 每个检测到的宿主最多注册一次；
- 不覆盖不属于 CarbonGate 的同名 `carbongate` 配置；
- 验证新注册，失败时自动回滚；
- Codex 接入时安装内置 CarbonGate Skill，但不替换已有同名 Skill；
- 将默认 CLI 目录加入用户 Shell 的 PATH，供新终端自动使用；
- 不下载依赖，不启动后台服务。

省略 `--setup` 或 `-Setup` 可仅安装而不修改宿主。使用 `--prefix PATH` 或
`-Prefix PATH` 可指定目录。

| 项目 | macOS/Linux | Windows |
|---|---|---|
| CLI | `~/.local/bin/carbon` | `%LOCALAPPDATA%\CarbonGate\bin\carbon.exe` |
| Java 运行时 | 不需要 | 不需要 |
| 状态/配置 | `~/.carbongate/` | `%USERPROFILE%\.carbongate\` |

所有平台都可用 `CARBON_HOME` 覆盖状态目录。

macOS/Linux 可用 `--version 0.3.1` 固定版本，Windows 使用 `-Version 0.3.1`。
离线镜像可覆盖 `CARBONGATE_MANIFEST_URL` 和
`CARBONGATE_RELEASE_BASE_URL`；如使用 `file://`，还必须显式设置
`CARBONGATE_ALLOW_FILE_URLS=1`。

### 手动校验次选方式

如果环境不允许直接执行下载的脚本，可先下载并审阅引导安装器；也可以从最新
Release 手动下载安装包和 `SHA256SUMS`。完成校验和解压后运行：

```bash
./carbongate-VERSION-PLATFORM/install.sh --setup
```

```powershell
.\carbongate-VERSION-windows-x64\install.ps1 -Setup
```

Release 同时保留 `carbongate-VERSION.tar.gz` 和 `.zip` 便携 JAR 包，供已有
Java 21 环境、macOS Intel 和企业评估使用。

### 安装后验证

```bash
carbon version
carbon doctor
carbon status
carbon rules
```

## 2. 次选：从源码安装

开发、测试分支或构建企业组件时使用此方式，需要完整 JDK 21 工具链。

### macOS 和 Linux

```bash
git clone https://github.com/soya7700/CarbonGate.git
cd CarbonGate
./scripts/install.sh --setup
export PATH="$HOME/.local/bin:$PATH"
carbon doctor
```

通过 `--host codex,claude` 指定宿主，或用 `--prefix /opt/carbongate`
指定安装目录。

### Windows

```powershell
git clone https://github.com/soya7700/CarbonGate.git
Set-Location CarbonGate
powershell -ExecutionPolicy Bypass -File .\scripts\install.ps1 -Setup
$env:Path = "$env:LOCALAPPDATA\CarbonGate\bin;$env:Path"
carbon doctor
```

按需使用 `-Hosts "codex,claude"` 或 `-Prefix "C:\Tools\CarbonGate"`。
源码安装器在本地编译，保留已有配置，不下载构建依赖。

## 3. 本地 Agent 与 MCP 接入

### CarbonGate Skill

安装时选择 Codex 后，内置的
[CarbonGate Skill](skills/carbongate/SKILL.md) 支持自然语言任务，例如：

- “查看最近被 CarbonGate 拦截的事项。”
- “列出待授权事项和当前规则。”
- “将 CarbonGate 切换为每次授权模式。”
- “保护当前项目使用的 MCP Server。”

Skill 调用本地 `carbon` CLI 并准确报告覆盖范围，不会把仅控制连接描述成
已经保护了宿主内置工具。

### 接入支持的宿主

macOS、Linux、Windows 使用相同命令：

```bash
carbon setup
carbon setup --host codex,claude,openclaw
carbon setup --host codex --dry-run
carbon integrations list
carbon doctor
```

自动适配器覆盖 Codex CLI（macOS 上也会识别 ChatGPT 桌面应用内置的 CLI）、Claude Code、OpenClaw、Qoder、
CodeBuddy/WorkBuddy CLI、Gemini CLI 和 GitHub Copilot CLI。通用 stdio MCP、
WorkBuddy 桌面端和 Coze/扣子使用引导或描述符：

```bash
carbon integrations guide generic-stdio
carbon integrations export generic-stdio --format mcp-json
carbon integrations guide coze
```

初次宿主接入的覆盖范围是 `CONTROL_ONLY`：提供状态、规则、拦截、授权和模式
管理。真正拦截需要使用受保护路由。

### 保护 MCP Server

Skill 推荐的原子化流程：

```bash
carbon protect /absolute/project/path --name filesystem --host codex -- npx some-mcp-server
carbon protections
carbon unprotect filesystem --host codex
```

使用 `--host generic` 时，CarbonGate 只保存保护路由并输出可移植描述符，不会
声称修改了不支持的宿主。

底层可复用 Profile 流程：

```bash
carbon mcp profile add filesystem \
  --workspace /absolute/project/path \
  -- npx some-mcp-server
carbon mcp profile show filesystem
carbon mcp profile export filesystem --format mcp-json
```

Windows 使用 Windows 工作区路径和 `npx.cmd` 等启动器。导出配置会运行
`carbon mcp profile run filesystem`，使上游 `tools/call` 经过 CarbonGate，
并明确标记为 `mcp_only` 覆盖。

Profile 原子写入 `$CARBON_HOME/mcp/profiles.json`，最多 100 个、总计 1 MiB，
不占用每日事件日志额度。`--token`、`--api-key` 等密钥参数会被拒绝；凭据应由
上游进程环境提供。

临时代理方式：

```text
carbon mcp proxy --workspace /absolute/project/path -- ORIGINAL_SERVER [ARGS...]
```

`carbon run --workspace /project -- your-agent` 只注入连接信息，不是操作系统
Sandbox，也不能阻止子进程绕过 CarbonGate。

## 4. Core 控制与配置

### 常用命令

```bash
carbon status
carbon rules
carbon blocked --limit 20
carbon approvals list
carbon approvals approve <id>
carbon approvals deny <id>
carbon control "每次操作都需要授权"
carbon control "恢复平衡模式"
carbon doctor
```

授权后 Agent 必须重试完全相同的操作；授权只能使用一次，24 小时后过期。

### 执行模式

| 模式 | 行为 |
|---|---|
| `BALANCED` | 按风险允许、询问或拒绝 |
| `WARN` | 告警但允许 |
| `APPROVAL` | 每个操作都要求授权 |
| `BLOCK` | 拒绝全部 Agent 操作 |

自然语言控制同时支持中文和英文意图。

### 配置文件

默认路径是 `~/.carbongate/carbon.conf` 或
`%USERPROFILE%\.carbongate\carbon.conf`：

```properties
mode=BALANCED
rules.shell.enabled=true
rules.filesystem.enabled=true
rules.network.enabled=true
rules.secrets.enabled=true
audit.mode=LOCAL_MINIMAL
audit.local.dailyLimitBytes=10000000
audit.enterprise.directory=enterprise-audit
audit.enterprise.dailyLimitBytes=100000000
alerts.consoleDailyLimit=100
```

通过校验后的命令管理配置：

```bash
carbon config path
carbon config show
carbon config set rules.network.enabled false
carbon config set audit.local.dailyLimitBytes 10000000
```

### 本地日志

本地 CLI 和 Agent 默认使用 `LOCAL_MINIMAL`：

- 只持久化完整拦截和内部错误；
- 不记录允许、普通告警和待授权事件；
- 拦截和错误记录共享每日 10,000,000 字节硬上限；
- 每条精简记录最多 1,024 字节，写盘前先脱敏。

详见[查询、控制、授权与日志](docs/control-and-logging.md)。

## 5. 企业组件

企业产品可选安装，与 Core 分开构建，通过有界 stdio 协议运行在 Core JVM 之外。

### 组件目录

| 组件 | 类型 | 功能 | 文档 |
|---|---|---|---|
| Sensitive Data Baseline | Pack | 个人身份/联系/家庭及企业财务/会员资产规则 | [Rule Packs](docs/rule-packs.md) |
| Sensitive Data Provider | Provider / `inspect` | 检测敏感数据但不返回匹配原文 | [DLP Provider](docs/sensitive-data-provider.md) |
| Approval Policy Provider | Provider / `authorize` | 稳定输出 `allow`、`ask`、`deny` | [Approval Provider](docs/approval-provider.md) |
| Container Sandbox | Sandbox / `sandbox` | Docker/Podman 默认拒绝式隔离 | [Container Sandbox](docs/container-sandbox.md) |
| Enterprise Audit Provider | Provider / `audit` | 脱敏、有界、SHA-256 哈希链 JSONL 审计 | [Audit Provider](docs/enterprise-audit-provider.md) |

生命周期和协议详见
[Enterprise Component Host](docs/enterprise-components.md)。

### 构建

macOS 和 Linux：

```bash
./scripts/build-enterprise.sh
./scripts/build-pack.sh
./scripts/build-provider.sh
./scripts/build-approval.sh
./scripts/build-audit.sh
./scripts/build-sandbox.sh
```

Windows PowerShell 使用对应 `.ps1`：

```powershell
.\scripts\build-enterprise.ps1
.\scripts\build-pack.ps1
.\scripts\build-provider.ps1
.\scripts\build-approval.ps1
.\scripts\build-audit.ps1
.\scripts\build-sandbox.ps1
```

### 安装与启用

```bash
./build/carbon-enterprise install build/sensitive-data-baseline-1.0.0.carbon
./build/carbon-enterprise enable sensitive-data-baseline 1.0.0
./build/carbon-enterprise install build/sensitive-data-provider-1.0.0.carbon
./build/carbon-enterprise enable sensitive-data-provider 1.0.0
./build/carbon-enterprise install build/approval-policy-provider-1.0.0.carbon
./build/carbon-enterprise enable approval-policy-provider 1.0.0
./build/carbon-enterprise install build/enterprise-audit-provider-1.0.0.carbon
./build/carbon-enterprise enable enterprise-audit-provider 1.0.0
./build/carbon-enterprise doctor
```

Container Sandbox 单独安装，只有 Docker 或 Podman 可用时才能启用。它要求本地
已有通过摘要固定的镜像，禁止拉取和联网，默认以只读方式挂载工作区。

### Guard Pipeline

显式企业管线：

```text
inspect -> authorize -> sandbox（仅 allow 后）-> audit
```

```bash
./build/carbon-enterprise guard \
  '{"action":"read","risk":"low","content":"需要检查的文本"}'
```

`fail_closed` 会把结果改为 `deny`；`fail_open` 会作为失败步骤明确返回。审计只
接收生成的事件 ID、动作、风险、决策和组件名。详见
[Enterprise Guard Pipeline](docs/enterprise-pipeline.md)。

### 自定义 Pack

Pack 是纯数据，不能执行代码，也不能注入任意正则表达式：

```json
{
  "id": "company.internal-label",
  "audience": "enterprise",
  "category": "enterprise.internal",
  "severity": "high",
  "match": {"type": "keywords", "terms": ["仅限内部"]}
}
```

固定检测器包括 `email`、`phone_cn`、`id_cn`、`bank_card` 和 `api_secret`。

### 组件签名

使用 JDK 21 原生 Ed25519 验证发布者：

```bash
./build/carbon-enterprise trust generate company-release /secure/keys
./build/carbon-enterprise trust add company-release /secure/keys/company-release.public.x509
./build/carbon-enterprise package source component.carbon \
  --sign company-release /secure/keys/company-release.private.pk8
./build/carbon-enterprise trust policy require_signed
```

私钥不会进入组件包。详见
[组件签名与信任策略](docs/component-trust.md)。

## 6. Java 21 项目集成

通过 `./scripts/build.sh` 生成 `build/carbongate.jar`。CarbonGate 暂未发布到
公共 Maven 仓库，请直接引用 JAR 或上传到企业内部仓库。

### 推荐：Sidecar

```bash
CARBON_HOME=/var/lib/carbongate \
  carbon gateway --profile strict --port 8765 --workspace /srv/app/workspace
```

```java
try (var client = new CarbonGateClient(URI.create("http://127.0.0.1:8765"))) {
    var result = client.evaluate(Action.shell("git status", Path.of("/srv/app/workspace")));
    if (result.decision() != Decision.ALLOW) {
        throw new SecurityException(result.reason());
    }
}
```

Gateway 只监听 `127.0.0.1`，没有远程认证，不能直接暴露到网络。

### 同 JVM 集成

```java
var runtime = CarbonGateRuntime.fromConfig(
        Path.of("/var/lib/carbongate"), PolicyProfile.STRICT);
var result = runtime.guard().evaluate(
        Action.shell("git status", Path.of("/srv/app/workspace")));
if (result.decision() == Decision.ALLOW) {
    // 执行真实操作。
}
```

业务代码必须先评估再执行，`ASK` 不代表许可。企业 Java 服务还可使用
`CarbonGateRuntime.enterprise(...)` 或自定义 `AuditSink`；它与可选企业组件
管线相互独立。

## 7. 安全、验证与许可

CarbonGate 仍处于早期安全项目阶段，不是透明系统调用拦截器。Core 的文件检查
不会创建 mount namespace 或 Chroot。敌对工作负载仍应运行在正确配置的容器或
操作系统 Sandbox 中。

使用前阅读[威胁模型](docs/threat-model.md)，安全问题通过
[SECURITY.md](SECURITY.md) 报告。

运行完整验证：

```bash
./scripts/verify.sh
```

验证会以 `--release 21 -Xlint:all -Werror` 编译，测试 Core 和全部可选组件，
验证安装包和跨平台脚本，检查体积预算，并拒绝未声明依赖。

CarbonGate 使用 [Apache License 2.0](LICENSE)。贡献和分发产物必须遵守
[依赖与许可策略](docs/dependency-policy.md)，并保持
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) 最新。
