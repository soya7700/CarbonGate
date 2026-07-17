# CarbonGate

面向 AI Agent 与 MCP 服务的本地优先零信任安全网关。

[English](README.md)

CarbonGate 在命令或工具调用执行前进行评估，应用工作区和网络外发策略，
自动脱敏敏感信息，并为风险操作提供明确的人工授权流程。

## 选择接入方式

| 使用目标 | 从这里开始 |
|---|---|
| 保护开发电脑上的命令 | [推荐一键安装](#1-推荐一键安装) |
| 开发或修改 CarbonGate | [源码安装](#2-次选从源码安装) |
| 保护 Codex、OpenClaw 或其他 MCP 宿主 | [Agent 与 MCP 接入](#3-接入-codexopenclaw-或-mcp-宿主) |
| 集成到 Java 21 业务应用 | [Java 项目集成](#4-集成-java-21-项目) |
| 启用企业详细审计 | [企业详细审计](#企业详细审计) |
| 开发可选企业组件 | [Enterprise Component Host](docs/enterprise-components.md) |
| 编写纯数据的敏感规则包 | [Rule Packs v1](docs/rule-packs.md) |
| 使用可选 Provider 检查敏感数据 | [Sensitive Data Provider](docs/sensitive-data-provider.md) |
| 使用可选容器 Sandbox 执行命令 | [Container Sandbox Provider](docs/container-sandbox.md) |

## 核心能力

- Shell 命令风险分析，返回 `allow`、`ask` 或 `deny`
- 配置工作区内的文件路径穿越和符号链接逃逸检测
- 网络外发风险分析与敏感信息泄漏检测
- 密码、Token、API Key 和隐私内容脱敏
- CLI、MCP stdio 代理、本地 HTTP 网关和 Java 21 API
- 平衡、仅警告、每次授权和完全拦截四种执行模式
- 一次性授权、拦截记录查询和可配置规则开关
- 本地 Agent 精简日志与企业详细审计模式

> [!IMPORTANT]
> CarbonGate 只能管控经过其 CLI、HTTP 网关、Java API 或 MCP 代理的操作。
> `carbon run` 是集成启动器，不是操作系统沙箱。对不可信工作负载仍应使用
> Docker、容器或操作系统级沙箱。

CarbonGate 当前处于早期 MVP 阶段。透明系统调用拦截、真正的挂载命名空间
或 Chroot 文件系统虚拟化尚未实现。

## 环境要求

- 推荐的预编译安装只需要 Java 21 或更高版本（`java`）
- 只有源码安装需要完整 JDK 21（`java`、`javac`、`jar`）和 Git
- macOS、Linux，或 Windows PowerShell 5.1 及以上版本

CarbonGate 运行时没有第三方源码或运行时依赖。
Core 的包体、依赖和组件边界由
[轻量架构决策](docs/architecture.md)及自动化预算测试共同约束。

## 1. 推荐：一键安装

普通 CLI、Codex、Claude Code、OpenClaw、Qoder、CodeBuddy、Gemini CLI
和 Copilot CLI 用户推荐使用预编译压缩包。该方式不在用户电脑上编译源码，
只要求 Java 21，安装步骤更短、平台差异更少。

macOS/Linux 下载并解压 `.tar.gz`，Windows 下载并解压 `.zip`。两种包包含
同一套经过测试的 JAR、配置示例、中英文文档、许可证和对应平台启动器。

### macOS 和 Linux

在解压目录运行一条安装命令：

```bash
./carbongate-VERSION/install.sh --setup
```

只接入指定的 AI CLI：

```bash
./carbongate-VERSION/install.sh --host codex,claude,openclaw
```

默认命令位置是 `~/.local/bin/carbon`。如果尚未加入 `PATH`：

```bash
export PATH="$HOME/.local/bin:$PATH"
```

### Windows

在解压目录打开 PowerShell，运行：

```powershell
powershell -ExecutionPolicy Bypass -File .\carbongate-VERSION\install.ps1 -Setup
```

只接入指定的 AI CLI：

```powershell
.\carbongate-VERSION\install.ps1 -Hosts "codex,claude,openclaw"
```

默认启动器是 `%LOCALAPPDATA%\CarbonGate\bin\carbon.cmd`。当前会话立即使用：

```powershell
$env:Path = "$env:LOCALAPPDATA\CarbonGate\bin;$env:Path"
```

### 安装器保障

- 确认当前 Java 可以运行以 JDK 21 为目标编译的 CarbonGate JAR
- 只安装压缩包中的 JAR 和一个小型平台启动器
- 仅在 `carbon.conf` 不存在时初始化，绝不覆盖已有配置
- 宿主注册具备幂等性，不会重复添加 CarbonGate 自己管理的配置
- 发现外部同名 `carbongate` MCP 配置时拒绝覆盖
- 新增注册后立即校验，失败时自动回滚
- 不下载运行时依赖，不安装后台常驻服务
- 选择 Codex 时安装包内置的 CarbonGate Skill；已有同名 Skill 时不覆盖

macOS/Linux 使用 `--prefix PATH`、Windows 使用 `-Prefix PATH` 可以指定安装
目录。不希望修改任何 AI 宿主配置时，省略 `--setup`/`-Setup` 即可。

### 安装目录

| 内容 | macOS/Linux 默认位置 | Windows 默认位置 |
|---|---|---|
| CLI | `~/.local/bin/carbon` | `%LOCALAPPDATA%\CarbonGate\bin\carbon.cmd` |
| JAR | `~/.local/lib/carbongate/carbongate.jar` | `%LOCALAPPDATA%\CarbonGate\lib\carbongate\carbongate.jar` |
| 配置与状态 | `~/.carbongate/` | `%USERPROFILE%\.carbongate\` |

所有平台都可以通过 `CARBON_HOME` 修改配置和状态目录。

### 安装后检查

```bash
carbon version
carbon doctor
carbon status
carbon rules
```

`carbon doctor` 会一次性检查 Java、状态目录、配置完整性、本地日志每天 10 MB
上限、安装 JAR、接入注册表以及检测到的宿主，并返回机器可读结果。

## 2. 次选：从源码安装

只有开发 CarbonGate、测试指定分支或修改 Java 实现时才建议使用源码安装。
该方式需要 Git 和完整 JDK 21 工具链。

### macOS

安装前确认 JDK 21：

```bash
java -version
javac -version
```

克隆项目并安装到当前用户：

```bash
git clone https://github.com/soya7700/CarbonGate.git
cd CarbonGate
./scripts/install.sh --setup
```

`--setup` 会自动识别本机支持的 AI CLI，并且每个只注册一次。只接入指定宿主
可以使用 `--host codex,claude`。

默认命令位置是 `~/.local/bin/carbon`。如果当前终端找不到命令：

```bash
export PATH="$HOME/.local/bin:$PATH"
carbon version
```

要让 PATH 长期生效，可以把 `export` 加入 `~/.zshrc` 或当前 Shell 的配置文件。

### Linux

通过系统包管理器或企业指定的 JDK 供应商安装 JDK 21，然后确认版本：

```bash
java -version
javac -version
```

克隆并安装：

```bash
git clone https://github.com/soya7700/CarbonGate.git
cd CarbonGate
./scripts/install.sh --setup
export PATH="$HOME/.local/bin:$PATH"
carbon version
```

可以把 `export` 加入 `~/.profile`、`~/.bashrc` 或当前 Shell 配置文件。

macOS 和 Linux 可以指定其他安装目录：

```bash
./scripts/install.sh --prefix /opt/carbongate
```

### Windows

打开 PowerShell 并确认 JDK 21：

```powershell
java -version
javac -version
```

克隆项目并运行 Windows 安装脚本：

```powershell
git clone https://github.com/soya7700/CarbonGate.git
Set-Location CarbonGate
powershell -ExecutionPolicy Bypass -File .\scripts\install.ps1
```

安装后立即接入检测到的 AI CLI：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\install.ps1 -Setup
# 或者只接入指定宿主：
.\scripts\install.ps1 -Hosts "codex,claude"
```

默认命令位置：

```text
%LOCALAPPDATA%\CarbonGate\bin\carbon.cmd
```

在当前 PowerShell 会话立即使用：

```powershell
$env:Path = "$env:LOCALAPPDATA\CarbonGate\bin;$env:Path"
carbon version
```

要让新终端也可以使用，请在 Windows“环境变量”的用户 `Path` 中加入
`%LOCALAPPDATA%\CarbonGate\bin`。也可以指定安装目录：

```powershell
.\scripts\install.ps1 -Prefix "C:\Tools\CarbonGate"
```

PowerShell 安装脚本使用本机 JDK 编译，生成 `carbon.cmd`，不会下载额外依赖。
源码安装器同样不会覆盖已有配置，并支持与推荐安装器一致的
`--setup`/`-Setup` 和宿主选择参数。

## 3. 接入 Codex、OpenClaw 或 MCP 宿主

### 推荐：CLI 一条命令接入

安装后，只要 `carbon` 已加入 `PATH`，macOS、Linux 和 Windows 使用同一套
命令：

```bash
# 自动识别已安装的受支持宿主，并且每个只配置一次
carbon setup

# 明确指定需要接入的宿主
carbon setup --host codex,claude,openclaw

# 只预览，不修改宿主配置
carbon setup --host codex --dry-run

# 查看接入状态并进行健康检查
carbon integrations list
carbon doctor
```

PowerShell 参数完全相同；如果安装目录还没有加入用户 `Path`，请使用
`carbon.cmd`。

当前已实现自动适配的 CLI：

| 宿主 | 识别的命令 | 配置范围 | 当前覆盖等级 |
|---|---|---|---|
| OpenAI Codex CLI | `codex` | 宿主用户配置 | 仅控制面 |
| Claude Code | `claude` | 用户级 | 仅控制面 |
| OpenClaw | `openclaw` | 宿主配置 | 仅控制面 |
| Qoder CLI | `qodercli` | 用户级 | 仅控制面 |
| CodeBuddy / WorkBuddy CLI | `codebuddy` | 宿主配置 | 仅控制面 |
| Gemini CLI | `gemini` | 用户级 | 仅控制面 |
| GitHub Copilot CLI | `copilot` | 宿主配置 | 仅控制面 |

没有稳定注册 CLI 的产品通过引导目录接入：

| 目标 | 命令 | 结果 |
|---|---|---|
| 大多数本地 stdio MCP 宿主 | `carbon integrations export generic-stdio --format mcp-json` | 可移植的命令和参数描述 |
| WorkBuddy 桌面端 | `carbon integrations guide workbuddy-desktop` | UI 操作步骤和可导出的 stdio 描述 |
| Coze / 扣子云端 | `carbon integrations guide coze` | 明确提示需要远程传输，不生成不安全的本地配置 |

`carbon setup` 会以固定名称 `carbongate` 注册无第三方依赖的
`carbon mcp serve` 控制服务。安装过程具备幂等性：CarbonGate 已管理的配置
不会重复添加；检测到同名但不属于 CarbonGate 的外部配置时只报告冲突，绝不
覆盖。新增后会再次校验，校验失败就自动回滚。管理权记录在
`~/.carbongate/integrations/registry.json`，也会随 `CARBON_HOME` 改变。
只删除 CarbonGate 自己管理的配置：

```bash
carbon integrations remove codex
```

导出操作只向标准输出返回内容，不会修改宿主配置：

```bash
carbon integrations guide generic-stdio
carbon integrations export generic-stdio --format descriptor
carbon integrations export generic-stdio --format mcp-json
carbon integrations export generic-stdio --format codex-toml
```

接入后，可以直接对兼容宿主说“查询 CarbonGate 最近拦截事项”“列出待授权
操作”或“把 CarbonGate 切换为每次授权”。控制服务提供状态、规则、完全拦截
记录、批准/拒绝和自然语言等级切换工具。

> [!IMPORTANT]
> 当前自动接入等级是 `CONTROL_ONLY`（仅控制面）：它让宿主能够查询和控制
> CarbonGate，但不会自动拦住宿主自带的 Shell、文件或网络工具。需要真正
> 执行拦截时，请通过下面的 MCP 代理、`carbon exec`、Java API 或 HTTP 网关
> 传递操作。

WorkBuddy 桌面端、Coze/扣子等暂时没有可确认的稳定本地 MCP 注册 CLI，当前
通过产品里的“添加 MCP Server”界面配置 `carbon mcp serve`。在厂商提供稳定
CLI 或配置契约前，这些产品保持引导式接入，避免错误修改用户配置。

### 保护已有 MCP Server

Codex 推荐使用安装包内置的 CarbonGate Skill。Skill 会先识别需要包装的上游
命令，再调用同一套原子 CLI 流程：

```bash
carbon protect /absolute/project/path --name filesystem --host codex -- npx some-mcp-server
carbon protections
carbon unprotect filesystem --host codex
```

命令会拒绝不属于 CarbonGate 的同名配置，新增后验证 Codex 注册，验证失败时
回滚 MCP 路由。使用 `--dry-run` 可以预览且不写入 CarbonGate 或宿主状态。

其他 stdio MCP 宿主使用 `--host generic`。CarbonGate 会保存受保护路由并返回
可移植描述，但不会声称已经修改宿主：

```bash
carbon protect /absolute/project/path --name filesystem --host generic -- npx some-mcp-server
```

推荐使用可复用的“受保护 MCP 路由”。只需创建一次，再为 Codex、OpenClaw、
WorkBuddy 或其他本地 stdio MCP 宿主导出配置：

```bash
carbon mcp profile add filesystem \
  --workspace /absolute/project/path \
  -- npx some-mcp-server
carbon mcp profile list
carbon mcp profile show filesystem
carbon mcp profile export filesystem --format mcp-json
```

Windows PowerShell 使用同一组命令，把工作区换成 Windows 路径，上游启动器可
使用 `npx.cmd`。导出的宿主配置会运行
`carbon mcp profile run filesystem`，因此上游的每个 `tools/call` 都会先经过
CarbonGate。支持 `descriptor`、`mcp-json` 和 `codex-toml` 三种只读导出格式，
并且明确标注覆盖等级为 `mcp_only`。

路由以原子方式保存在 `$CARBON_HOME/mcp/profiles.json`，默认位置是
`~/.carbongate/mcp/profiles.json`。注册表最多 100 条、最大 1 MiB；它是精简
配置状态而不是事件日志，不占用本地每天 10 MB 日志额度。CarbonGate 会拒绝
疑似密钥的命令参数，以及 `--token`、`--api-key` 等凭据选项；凭据应通过
上游进程环境提供，禁止明文写入路由。

不修改宿主配置也可以管理路由：

```bash
carbon mcp profile export filesystem --format descriptor
carbon mcp profile export filesystem --format codex-toml
carbon mcp profile remove filesystem
```

临时或脚本场景仍可直接通过代理包装已有的 stdio MCP Server。把原服务命令
放在 `--` 后：

```text
carbon mcp proxy --workspace /absolute/project/path -- ORIGINAL_SERVER [ARGS...]
```

macOS 或 Linux 通用 MCP 配置：

```json
{
  "mcpServers": {
    "filesystem": {
      "command": "/absolute/path/to/carbon",
      "args": [
        "mcp",
        "proxy",
        "--workspace",
        "/absolute/path/to/project",
        "--",
        "npx",
        "some-mcp-server"
      ]
    }
  }
}
```

Windows 使用 `carbon.cmd` 和 Windows 路径：

```json
{
  "mcpServers": {
    "filesystem": {
      "command": "C:\\Users\\YOU\\AppData\\Local\\CarbonGate\\bin\\carbon.cmd",
      "args": [
        "mcp",
        "proxy",
        "--workspace",
        "C:\\work\\project",
        "--",
        "npx.cmd",
        "some-mcp-server"
      ]
    }
  }
}
```

在 Codex、OpenClaw 或其他支持 stdio MCP 的宿主中，用上述代理配置替换原
MCP Server 配置。CarbonGate 会在转发 `tools/call` 前完成安全判断。

告警会出现在宿主捕获的 `stderr` 或工具响应中。需要人工授权时运行：

```bash
carbon approvals list
carbon approvals approve <id>
```

Agent 必须重新发起完全相同的操作。授权只使用一次，24 小时后自动过期。
拒绝待授权请求：

```bash
carbon approvals deny <id>
```

兼容的 Agent 也可以通过以下方式获得 CarbonGate 连接信息：

```bash
carbon run --workspace /path/to/project -- your-agent-command
```

该命令会注入 `CARBON_ENDPOINT`、`CARBON_WORKSPACE`、`CARBON_PROFILE` 和
`CARBON_MODE`，但不能阻止子进程绕过 CarbonGate 直接访问系统。

## 4. 集成 Java 21 项目

CarbonGate 当前尚未发布到公共 Maven 仓库，需要先从源码构建 JAR：

```bash
./scripts/build.sh
# build/carbongate.jar
```

Windows 运行 `scripts\install.ps1` 后也会生成 `build\carbongate.jar`。

### Gradle

把 JAR 复制到业务项目的 `libs/` 目录：

```kotlin
dependencies {
    implementation(files("libs/carbongate.jar"))
}
```

### Maven

先安装到本机 Maven 仓库：

```bash
mvn install:install-file \
  -Dfile=/absolute/path/to/carbongate.jar \
  -DgroupId=io.carbongate \
  -DartifactId=carbongate \
  -Dversion=0.2.0 \
  -Dpackaging=jar
```

添加依赖：

```xml
<dependency>
  <groupId>io.carbongate</groupId>
  <artifactId>carbongate</artifactId>
  <version>0.2.0</version>
</dependency>
```

### 推荐：Sidecar 独立网关

独立启动 CarbonGate：

```bash
CARBON_HOME=/var/lib/carbongate \
  carbon gateway --profile strict --port 8765 --workspace /srv/app/workspace
```

Java 应用调用网关：

```java
import io.carbongate.model.Action;
import io.carbongate.model.Decision;
import io.carbongate.sdk.CarbonGateClient;

import java.net.URI;
import java.nio.file.Path;

try (var client = new CarbonGateClient(URI.create("http://127.0.0.1:8765"))) {
    var result = client.evaluate(Action.shell("git status", Path.of("/srv/app/workspace")));
    if (result.decision() != Decision.ALLOW) {
        throw new SecurityException(result.reason());
    }
    // 只有 ALLOW 后才执行真实操作
}
```

网关只监听 `127.0.0.1`，但当前没有远程认证，不应直接暴露到网络。

### 同 JVM 集成

```java
import io.carbongate.model.Action;
import io.carbongate.model.Decision;
import io.carbongate.policy.PolicyProfile;
import io.carbongate.runtime.CarbonGateRuntime;

import java.nio.file.Path;

var runtime = CarbonGateRuntime.fromConfig(
        Path.of("/var/lib/carbongate"),
        PolicyProfile.STRICT
);
var result = runtime.guard().evaluate(
        Action.shell("git status", Path.of("/srv/app/workspace"))
);
if (result.decision() == Decision.ALLOW) {
    // 执行真实操作
}
```

业务代码必须在真实操作前完成评估，并且只在 `Decision.ALLOW` 时继续。
`ASK` 不代表允许。

### 企业详细审计

```java
var runtime = CarbonGateRuntime.enterprise(
        Path.of("/var/lib/carbongate"),
        Path.of("/var/log/company/carbongate"),
        PolicyProfile.STRICT,
        100_000_000L
);
```

企业模式详细记录允许、待授权、批准、拒绝和内部错误。必要审计写入失败时
会 fail closed 并返回 `DENY`。企业可以实现 `AuditSink`，把事件写入已有的
SIEM、数据库或日志平台。

## 5. 配置和管理 CarbonGate

### 核心命令

```text
carbon status
carbon rules
carbon config init|show|path|set <key> <value>
carbon blocked [--limit 20]
carbon approvals list|approve <id>|deny <id>
carbon mode show|set <自然语言级别>
carbon control "自然语言级别指令"
carbon setup [--host HOST[,HOST...]] [--all] [--dry-run]
carbon protect [WORKSPACE] --name NAME [--host codex|generic] [--dry-run] -- SERVER [ARGS...]
carbon protections [list]
carbon unprotect <name> [--host codex|generic]
carbon integrations list|remove <host>|guide <host>|export <host> [--format FORMAT]
carbon doctor
carbon check [--profile strict|balanced|audit] [--workspace PATH] -- COMMAND
carbon exec [--profile strict|balanced|audit] [--workspace PATH] -- COMMAND
carbon gateway [--profile PROFILE] [--workspace PATH] [--port 8765]
carbon mcp proxy [--profile PROFILE] [--workspace PATH] -- SERVER [ARGS...]
carbon mcp serve
carbon mcp profile list|show <name>|add <name> [--workspace PATH] [--replace] -- SERVER [ARGS...]
carbon mcp profile remove|run <name>|export <name> [--format FORMAT]
carbon redact TEXT
carbon run [--workspace PATH] -- AGENT [ARGS...]
carbon version
```

常用示例：

```bash
carbon check --workspace /path/to/project -- 'git status'
carbon exec --workspace /path/to/project -- 'touch result.txt'
carbon blocked --limit 20
carbon control "以后每次都要手动授权"
carbon control "恢复默认平衡模式"
```

### 配置文件

macOS/Linux 默认路径是 `~/.carbongate/carbon.conf`，Windows 默认路径是
`%USERPROFILE%\.carbongate\carbon.conf`。查看当前路径：

```bash
carbon config path
```

完整配置：

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

| 配置项 | 默认值 | 说明 |
|---|---:|---|
| `mode` | `BALANCED` | `BALANCED`、`WARN`、`APPROVAL` 或 `BLOCK` |
| `rules.shell.enabled` | `true` | Shell 命令风险规则 |
| `rules.filesystem.enabled` | `true` | 文件边界和路径风险规则 |
| `rules.network.enabled` | `true` | 网络外发风险规则 |
| `rules.secrets.enabled` | `true` | 敏感信息风险规则；基础脱敏仍会执行 |
| `audit.mode` | `LOCAL_MINIMAL` | `LOCAL_MINIMAL` 或 `ENTERPRISE_DETAILED` |
| `audit.local.dailyLimitBytes` | `10000000` | 本地日志每日合计硬上限，最大 10,000,000 字节 |
| `audit.enterprise.directory` | `enterprise-audit` | 企业审计目录；相对路径基于 `CARBON_HOME` |
| `audit.enterprise.dailyLimitBytes` | `100000000` | 企业审计每日安全上限 |
| `alerts.consoleDailyLimit` | `100` | 长期运行 MCP 代理的控制台告警上限 |

通过命令修改：

```bash
carbon config set rules.network.enabled false
carbon config set audit.local.dailyLimitBytes 10000000
carbon config set audit.mode ENTERPRISE_DETAILED
```

规则和 `mode` 在下一次 Tool Call 生效；审计模式、目录和容量变更需要重启
长期运行的 Gateway 或 MCP 代理。未知配置项和非法值会被拒绝。

### 执行模式

| 模式 | 行为 |
|---|---|
| `BALANCED` | 按照风险自动允许、请求授权或拒绝 |
| `WARN` | 显示风险，但允许操作 |
| `APPROVAL` | 每个操作都需要一次性人工批准 |
| `BLOCK` | 拒绝所有 Agent 操作 |

支持中文或英文自然语言切换：

```bash
carbon control "切换到警告提醒"
carbon control "以后每次都要手动授权"
carbon control "完全拦截所有操作"
carbon control "恢复默认平衡模式"
```

`--profile strict|balanced|audit` 决定风险如何映射为决策，`mode` 是全局运行
控制级别。

### 安装健康检查

`carbon doctor` 会用一个机器可读结果检查 Java 版本、CarbonGate 状态目录、
配置文件、本地日志 10 MB 上限、控制服务启动命令、接入注册表和检测到的全部
宿主。CarbonGate 自己管理的注册损坏、外部同名冲突、JAR 丢失、注册表不可读
或其他系统检查失败时返回非零状态；没有安装的可选宿主只作为提示。

### 日志与告警

本地 CLI、Codex 和 OpenClaw 默认使用 `LOCAL_MINIMAL`：

- 只写完全拦截和内部错误
- 允许、警告和待授权事件不落盘
- 拦截与错误文件共享每天 10,000,000 字节硬上限
- 单条记录不超过 1,024 字节，长文本会在脱敏后截断
- `carbon status` 可以查询日志位置和当天用量

企业 Java 服务可以显式使用 `ENTERPRISE_DETAILED`，完整记录安全决策和授权
事件。详细说明见 [控制、授权与日志](docs/control-and-logging.md)。

## 安全与开发

将 CarbonGate 作为安全边界之前，请阅读[威胁模型](docs/threat-model.md)。
发现疑似漏洞时按照 [SECURITY.md](SECURITY.md) 报告。

运行完整校验：

```bash
./scripts/verify.sh
```

其他开发命令：

```bash
./scripts/test.sh
./scripts/build.sh
./scripts/functional-test.sh
./scripts/package.sh 0.2.0
```

打包会同时生成 `carbongate-0.2.0.tar.gz` 和
`carbongate-0.2.0.zip`。`scripts/package-test.sh` 会确认两种压缩包都包含各平台
启动器、压缩包安装器、文档、配置和许可证文件。

CarbonGate 使用 [Apache License 2.0](LICENSE)。分发的产物必须遵循
[依赖与许可证策略](docs/dependency-policy.md)，并及时维护
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
