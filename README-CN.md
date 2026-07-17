# CarbonGate

面向 AI Agent 与 MCP 服务的本地优先零信任安全网关。

[English](README.md)

CarbonGate 在命令或工具调用执行前进行评估，应用工作区和网络外发策略，
自动脱敏敏感信息，并为风险操作提供明确的人工授权流程。

## 选择接入方式

| 使用目标 | 从这里开始 |
|---|---|
| 保护开发电脑上的命令 | [安装 CLI](#1-安装-cli) |
| 保护 Codex、OpenClaw 或其他 MCP 宿主 | [Agent 与 MCP 接入](#2-接入-codexopenclaw-或-mcp-宿主) |
| 集成到 Java 21 业务应用 | [Java 项目集成](#3-集成-java-21-项目) |
| 启用企业详细审计 | [企业详细审计](#企业详细审计) |

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

- JDK 21 或更高版本，需要可以使用 `java`、`javac` 和 `jar`
- 从源码安装时需要 Git
- macOS、Linux，或 Windows PowerShell 5.1 及以上版本

CarbonGate 运行时没有第三方源码或运行时依赖。

## 1. 安装 CLI

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
./scripts/install.sh
```

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
./scripts/install.sh
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
carbon config init
carbon status
carbon rules
```

安装程序只会在配置不存在时初始化，不会覆盖已有的 `carbon.conf`。

## 2. 接入 Codex、OpenClaw 或 MCP 宿主

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

`carbon setup` 会以固定名称 `carbongate` 注册无第三方依赖的
`carbon mcp serve` 控制服务。安装过程具备幂等性：CarbonGate 已管理的配置
不会重复添加；检测到同名但不属于 CarbonGate 的外部配置时只报告冲突，绝不
覆盖。新增后会再次校验，校验失败就自动回滚。管理权记录在
`~/.carbongate/integrations/registry.json`，也会随 `CARBON_HOME` 改变。
只删除 CarbonGate 自己管理的配置：

```bash
carbon integrations remove codex
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

CarbonGate 通过代理包装已有的 stdio MCP Server。把原服务命令放在 `--` 后：

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

## 3. 集成 Java 21 项目

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

## 4. 配置和管理 CarbonGate

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
carbon integrations list|remove <host>
carbon doctor
carbon check [--profile strict|balanced|audit] [--workspace PATH] -- COMMAND
carbon exec [--profile strict|balanced|audit] [--workspace PATH] -- COMMAND
carbon gateway [--profile PROFILE] [--workspace PATH] [--port 8765]
carbon mcp proxy [--profile PROFILE] [--workspace PATH] -- SERVER [ARGS...]
carbon mcp serve
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

CarbonGate 使用 [Apache License 2.0](LICENSE)。分发的产物必须遵循
[依赖与许可证策略](docs/dependency-policy.md)，并及时维护
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
