# CarbonGate

面向 AI Agent 与 MCP 服务的本地优先零信任安全网关。

[English](README.md)

> **MVP 安全边界**
> CarbonGate 只能管控经过其 CLI、HTTP 网关、Java API 或 MCP 代理的命令与
> 工具调用。`carbon run` 只负责注入集成信息，不是操作系统沙箱。对不可信
> 工作负载仍应使用 Docker、容器或操作系统级沙箱。

## 项目简介

CarbonGate 在 Agent 执行操作之前进行安全判断，并提供以下能力：

- Shell 命令静态风险分析，返回 `allow`、`ask` 或 `deny`
- 文件路径边界检查，包括路径穿越与符号链接逃逸检测
- 网络外发风险分析与敏感信息泄漏检测
- 密码、Token、API Key 等敏感内容扫描和稳定脱敏
- MCP stdio 代理、HTTP 本地网关和无第三方依赖的 Java 21 API
- 警告、每次授权、完全拦截和平衡模式的自然语言切换
- 一次性人工授权队列以及拦截记录查询
- 本地 Agent 精简日志和企业 Java 应用详细审计两种策略

当前版本为早期 MVP。透明系统调用拦截、真正的挂载命名空间或 Chroot
虚拟文件系统不在当前版本能力范围内。

## 环境要求

- JDK 21 或更高版本
- macOS、Linux，或可以运行 JDK 21 与 Shell 脚本的环境
- 从源码安装时需要 Git

CarbonGate 核心和发布包目前没有第三方源码或运行时依赖。

## CLI 安装

### 从源码安装到当前用户

```bash
git clone https://github.com/soya7700/CarbonGate.git
cd CarbonGate
./scripts/install.sh
```

默认安装位置：

- 命令：`~/.local/bin/carbon`
- JAR：`~/.local/lib/carbongate/carbongate.jar`
- 配置和运行状态：`~/.carbongate/`

如果 `~/.local/bin` 不在 `PATH` 中：

```bash
export PATH="$HOME/.local/bin:$PATH"
```

可以指定安装目录：

```bash
./scripts/install.sh --prefix /opt/carbongate
```

安装脚本会使用 `javac --release 21` 构建 JAR，并在配置不存在时创建
`$CARBON_HOME/carbon.conf`，不会覆盖已有配置。

### 直接从源码目录运行

```bash
./scripts/build.sh
./build/carbon version
./build/carbon status
```

### 从打包文件运行

收到或手动生成发布包后，可以直接解压运行：

```bash
./scripts/package.sh 0.2.0
tar -xzf build/carbongate-0.2.0.tar.gz
./build/carbongate-0.2.0/bin/carbon version
```

项目不会自动创建 GitHub Release；Release 只在维护者明确要求时手动发布。

### 安装后检查

```bash
carbon version
carbon config init
carbon status
carbon rules
```

## CLI 使用

检查命令但不执行：

```bash
carbon check --workspace /path/to/project -- 'git status'
carbon check --workspace /path/to/project -- 'rm -rf /'
```

检查后执行；遇到 `ask` 时需要人工确认或一次性授权：

```bash
carbon exec --workspace /path/to/project -- 'touch result.txt'
```

启动仅监听回环地址的 HTTP 网关：

```bash
carbon gateway --port 8765 --workspace /path/to/project
curl -s http://127.0.0.1:8765/v1/health
```

调用评估接口：

```bash
curl -s http://127.0.0.1:8765/v1/evaluate \
  -H 'content-type: application/json' \
  -d '{"capability":"shell","operation":"execute","resource":"rm -rf /"}'
```

查询和控制：

```bash
carbon status
carbon rules
carbon blocked --limit 20
carbon approvals list
carbon approvals approve <id>
carbon approvals deny <id>
carbon config show
carbon config path
```

自然语言切换控制级别：

```bash
carbon control "切换到警告提醒"
carbon control "以后每次都要手动授权"
carbon control "完全拦截所有操作"
carbon control "恢复默认平衡模式"
```

完整命令列表：

```text
carbon status
carbon rules
carbon config init|show|path|set <key> <value>
carbon blocked [--limit 20]
carbon approvals list|approve <id>|deny <id>
carbon mode show|set <自然语言级别>
carbon control "自然语言级别指令"
carbon check [--profile strict|balanced|audit] [--workspace PATH] -- COMMAND
carbon exec [--profile strict|balanced|audit] [--workspace PATH] -- COMMAND
carbon gateway [--profile PROFILE] [--workspace PATH] [--port 8765]
carbon mcp proxy [--profile PROFILE] [--workspace PATH] -- SERVER [ARGS...]
carbon redact TEXT
carbon run [--workspace PATH] -- AGENT [ARGS...]
carbon version
```

## MCP、Codex 与 OpenClaw 接入

将原 MCP Server 命令放到 `carbon mcp proxy` 的 `--` 后面。通用 stdio MCP
配置示例：

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

在 Codex、OpenClaw 或其他支持 stdio MCP 的 Agent 中，把原 MCP Server
配置替换为上述代理形式即可。CarbonGate 会在转发 `tools/call` 前判断风险。
告警会出现在宿主捕获的 `stderr` 或工具响应中；需要授权时可运行：

```bash
carbon approvals list
carbon approvals approve <id>
```

Agent 必须重新发起完全相同的操作，授权只消费一次，24 小时后自动过期。

也可以使用集成启动器为兼容 Agent 注入 `CARBON_ENDPOINT`、
`CARBON_WORKSPACE`、`CARBON_PROFILE` 和 `CARBON_MODE`：

```bash
carbon run --workspace /path/to/project -- your-agent-command
```

`carbon run` 本身不能阻止子进程绕开 CarbonGate 直接访问系统。

## Java 21 项目引入

CarbonGate 当前还没有发布到 Maven Central。可以先构建 JAR：

```bash
./scripts/build.sh
# 生成 build/carbongate.jar
```

### Gradle 项目

将 JAR 复制到业务项目的 `libs/` 目录：

```kotlin
dependencies {
    implementation(files("libs/carbongate.jar"))
}
```

### Maven 项目

先安装到本机 Maven 仓库：

```bash
mvn install:install-file \
  -Dfile=/absolute/path/to/carbongate.jar \
  -DgroupId=io.carbongate \
  -DartifactId=carbongate \
  -Dversion=0.2.0 \
  -Dpackaging=jar
```

然后在 `pom.xml` 中加入：

```xml
<dependency>
  <groupId>io.carbongate</groupId>
  <artifactId>carbongate</artifactId>
  <version>0.2.0</version>
</dependency>
```

### 推荐：Sidecar/独立网关模式

独立启动 CarbonGate：

```bash
CARBON_HOME=/var/lib/carbongate \
  carbon gateway --profile strict --port 8765 --workspace /srv/application/workspace
```

Java 应用通过 SDK 调用：

```java
import io.carbongate.model.Action;
import io.carbongate.model.Decision;
import io.carbongate.sdk.CarbonGateClient;

import java.net.URI;
import java.nio.file.Path;

try (var client = new CarbonGateClient(URI.create("http://127.0.0.1:8765"))) {
    var result = client.evaluate(Action.shell("git status", Path.of("/srv/application/workspace")));
    if (result.decision() != Decision.ALLOW) {
        throw new SecurityException(result.reason());
    }
    // 只有 ALLOW 后才执行真实操作
}
```

Sidecar 模式便于独立升级策略和审计逻辑。当前 HTTP 网关只监听
`127.0.0.1`，但还没有远程认证，不应直接暴露到网络。

### 同 JVM 模式

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
        Action.shell("git status", Path.of("/srv/application/workspace"))
);
if (result.decision() == Decision.ALLOW) {
    // 执行真实操作
}
```

业务代码必须在操作执行前调用 `guard().evaluate(...)`，并且只在
`Decision.ALLOW` 时继续。`ASK` 不是允许结果。

### 企业详细审计模式

```java
var runtime = CarbonGateRuntime.enterprise(
        Path.of("/var/lib/carbongate"),
        Path.of("/var/log/company/carbongate"),
        PolicyProfile.STRICT,
        100_000_000L
);
```

企业模式记录允许、待授权、批准、拒绝和内部错误等详细事件；要求的审计
写入失败时会 fail closed，返回 `DENY`。企业也可以实现 `AuditSink`，将事件
送入现有 SIEM、数据库或日志平台。

Spring Boot Starter 和框架专用适配器将在核心 API 稳定后提供。

## 配置说明

配置文件位置：

- 默认：`~/.carbongate/carbon.conf`
- 自定义：设置环境变量 `CARBON_HOME=/custom/state/directory`
- 查看路径：`carbon config path`

生成默认配置：

```bash
carbon config init
```

完整配置示例：

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
| `mode` | `BALANCED` | 全局执行级别：`BALANCED`、`WARN`、`APPROVAL`、`BLOCK` |
| `rules.shell.enabled` | `true` | Shell 风险规则 |
| `rules.filesystem.enabled` | `true` | 文件边界和路径风险规则 |
| `rules.network.enabled` | `true` | 网络外发风险规则 |
| `rules.secrets.enabled` | `true` | 敏感信息风险规则；输出仍会进行基础脱敏 |
| `audit.mode` | `LOCAL_MINIMAL` | `LOCAL_MINIMAL` 或 `ENTERPRISE_DETAILED` |
| `audit.local.dailyLimitBytes` | `10000000` | 本地日志每日合计硬上限，最大 10,000,000 字节 |
| `audit.enterprise.directory` | `enterprise-audit` | 企业审计目录；相对路径基于 `CARBON_HOME` |
| `audit.enterprise.dailyLimitBytes` | `100000000` | 企业审计每日安全上限 |
| `alerts.consoleDailyLimit` | `100` | 长期运行 MCP 代理每日控制台告警数量上限 |

命令修改配置：

```bash
carbon config set rules.network.enabled false
carbon config set audit.local.dailyLimitBytes 10000000
carbon config set audit.mode ENTERPRISE_DETAILED
```

规则和 `mode` 在下一次 Tool Call 生效；审计模式、目录和容量变更需要重启
长期运行的 Gateway 或 MCP 代理。未知配置项和非法值会被拒绝。关闭规则会
降低保护能力，应经过安全评审。

### 执行模式

| 模式 | 行为 |
|---|---|
| `BALANCED` | 根据风险等级自动允许、请求授权或拒绝 |
| `WARN` | 显示风险，但允许操作 |
| `APPROVAL` | 每个操作都需要一次性人工批准 |
| `BLOCK` | 拒绝所有 Agent 操作 |

`--profile strict|balanced|audit` 决定风险等级如何映射为决策，`mode` 则是
运行时的全局控制级别。

## 日志与告警

本地 Codex、OpenClaw 和 CLI 安装默认使用 `LOCAL_MINIMAL`：

- 只写完全拦截和内部错误；允许、警告、待授权不落盘
- 拦截与错误文件共享每天 10,000,000 字节硬上限
- 单条记录不超过 1,024 字节，并在脱敏后截断长文本
- 日志位置可以通过 `carbon status` 查询

企业 Java 服务可以显式启用 `ENTERPRISE_DETAILED`，详细记录安全决策和授权
事件。更多说明见 [控制、授权与日志](docs/control-and-logging.md)。

## 开发、测试与许可证

```bash
./scripts/test.sh
./scripts/build.sh
./scripts/functional-test.sh
./scripts/verify.sh
./scripts/package.sh 0.2.0
```

`scripts/verify.sh` 是本地和 CI 的统一验证入口，包括 JDK 21 编译、单元测试、
功能测试和依赖/许可证检查。

CarbonGate 使用 [Apache License 2.0](LICENSE)。发布前必须遵循
[依赖与许可证策略](docs/dependency-policy.md)，并同步维护
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。安全边界和已知限制见
[威胁模型](docs/threat-model.md)，漏洞报告方式见 [SECURITY.md](SECURITY.md)。
