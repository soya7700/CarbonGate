# CarbonGate

Local-first zero-trust security gateway for AI agents and MCP servers.

[简体中文](README-CN.md)

CarbonGate evaluates commands and tool calls before execution, applies
workspace and egress policies, redacts secrets, and supports explicit human
approval for risky operations.

## Choose an integration path

| Goal | Start here |
|---|---|
| Protect commands on a developer machine | [Recommended one-command install](#1-recommended-one-command-installation) |
| Build or modify CarbonGate itself | [Install from source](#2-alternative-install-from-source) |
| Protect Codex, OpenClaw, or another MCP host | [Agent and MCP integration](#3-integrate-codex-openclaw-or-an-mcp-host) |
| Add checks to a Java 21 application | [Java integration](#4-integrate-a-java-21-application) |
| Enable detailed enterprise audit | [Enterprise audit](#enterprise-detailed-audit) |

## What CarbonGate provides

- Shell command risk analysis with `allow`, `ask`, and `deny` decisions
- File traversal and symlink-escape detection within a configured workspace
- Network egress risk analysis and sensitive-data leak detection
- Password, token, API key, and private-data redaction
- CLI, MCP stdio proxy, loopback HTTP gateway, and Java 21 API
- Balanced, warn-only, approval-required, and block-all enforcement modes
- One-time approvals, blocked-event queries, and configurable rule switches
- Compact local-Agent logs and detailed enterprise audit modes

> [!IMPORTANT]
> CarbonGate enforces only operations that pass through its CLI, HTTP gateway,
> Java API, or MCP proxy. `carbon run` is an integration launcher, not an
> operating-system sandbox. Use a container or OS sandbox for hostile workloads.

CarbonGate is currently an early MVP. Transparent syscall interception and real
mount-namespace or Chroot filesystem virtualization are not implemented yet.

## Requirements

- Java 21 or newer (`java`) for the recommended prebuilt installation
- JDK 21 or newer (`java`, `javac`, and `jar`) plus Git only when installing from source
- macOS, Linux, or Windows PowerShell 5.1+

The runtime has no third-party source or runtime dependencies.
The enforced size, dependency, and component boundaries are documented in
[the lightweight architecture decision](docs/architecture.md).

## 1. Recommended: one-command installation

Use a prebuilt CarbonGate archive for normal CLI, Codex, Claude Code,
OpenClaw, Qoder, CodeBuddy, Gemini CLI, or Copilot CLI installations. This is
the recommended path because it does not compile source and needs only Java 21.

Download and extract the `.tar.gz` package on macOS/Linux or the `.zip` package
on Windows. Both packages contain the same tested JAR, configuration,
documentation, license notices, and platform launchers.

### macOS and Linux

Run the packaged installer from the extracted directory:

```bash
./carbongate-VERSION/install.sh --setup
```

To configure only selected AI CLIs:

```bash
./carbongate-VERSION/install.sh --host codex,claude,openclaw
```

The default command is `~/.local/bin/carbon`. If it is not already on `PATH`:

```bash
export PATH="$HOME/.local/bin:$PATH"
```

### Windows

Open PowerShell in the extracted directory and run:

```powershell
powershell -ExecutionPolicy Bypass -File .\carbongate-VERSION\install.ps1 -Setup
```

To configure only selected AI CLIs:

```powershell
.\carbongate-VERSION\install.ps1 -Hosts "codex,claude,openclaw"
```

The default launcher is
`%LOCALAPPDATA%\CarbonGate\bin\carbon.cmd`. Use it immediately with:

```powershell
$env:Path = "$env:LOCALAPPDATA\CarbonGate\bin;$env:Path"
```

### What the installer guarantees

- Verifies that Java can run the JDK 21-targeted CarbonGate JAR
- Installs only the packaged JAR and a small platform launcher
- Initializes `carbon.conf` only when it does not already exist
- Uses idempotent host registration and never duplicates a managed entry
- Refuses to overwrite an external same-name `carbongate` MCP entry
- Verifies each registration and rolls it back if verification fails
- Downloads no runtime dependencies and adds no background service

Use `--prefix PATH` on macOS/Linux or `-Prefix PATH` on Windows to choose
another installation directory. Omit `--setup`/`-Setup` when CarbonGate should
be installed without changing any AI host configuration.

### Installation layout

| Item | macOS/Linux default | Windows default |
|---|---|---|
| CLI | `~/.local/bin/carbon` | `%LOCALAPPDATA%\CarbonGate\bin\carbon.cmd` |
| JAR | `~/.local/lib/carbongate/carbongate.jar` | `%LOCALAPPDATA%\CarbonGate\lib\carbongate\carbongate.jar` |
| State and configuration | `~/.carbongate/` | `%USERPROFILE%\.carbongate\` |

Override the state directory on every platform with `CARBON_HOME`.

### Verify the installation

```bash
carbon version
carbon doctor
carbon status
carbon rules
```

`carbon doctor` checks Java, the state directory, configuration validity, the
10 MB local daily log cap, packaged JAR, integration registry, and detected
hosts in one machine-readable result.

## 2. Alternative: install from source

Use the source path when developing CarbonGate, testing a branch, or changing
the Java implementation. It requires Git and a full JDK 21 toolchain.

### macOS

Verify JDK 21 before installation:

```bash
java -version
javac -version
```

Clone and install for the current user:

```bash
git clone https://github.com/soya7700/CarbonGate.git
cd CarbonGate
./scripts/install.sh --setup
```

`--setup` detects supported local AI CLIs and registers CarbonGate once. Limit
the operation to selected hosts with `--host codex,claude`.

The default command is `~/.local/bin/carbon`. Add it to the current shell when
needed:

```bash
export PATH="$HOME/.local/bin:$PATH"
carbon version
```

To keep the PATH change, add the `export` line to `~/.zshrc` or your active
shell profile.

### Linux

Install a JDK 21 distribution with the operating system's package manager or
your approved JDK vendor, then verify it:

```bash
java -version
javac -version
```

Clone and install:

```bash
git clone https://github.com/soya7700/CarbonGate.git
cd CarbonGate
./scripts/install.sh --setup
export PATH="$HOME/.local/bin:$PATH"
carbon version
```

Add the `export` line to `~/.profile`, `~/.bashrc`, or the active shell profile
to make it persistent.

macOS and Linux can choose another installation prefix:

```bash
./scripts/install.sh --prefix /opt/carbongate
```

### Windows

Open PowerShell and confirm JDK 21:

```powershell
java -version
javac -version
```

Clone and run the Windows installer:

```powershell
git clone https://github.com/soya7700/CarbonGate.git
Set-Location CarbonGate
powershell -ExecutionPolicy Bypass -File .\scripts\install.ps1
```

Install and configure detected AI CLIs in one step with:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\install.ps1 -Setup
# Or only selected hosts:
.\scripts\install.ps1 -Hosts "codex,claude"
```

The default command is:

```text
%LOCALAPPDATA%\CarbonGate\bin\carbon.cmd
```

Use it immediately in the current PowerShell session:

```powershell
$env:Path = "$env:LOCALAPPDATA\CarbonGate\bin;$env:Path"
carbon version
```

For future terminals, add `%LOCALAPPDATA%\CarbonGate\bin` to the user `Path`
in Windows Environment Variables. A custom installation directory is supported:

```powershell
.\scripts\install.ps1 -Prefix "C:\Tools\CarbonGate"
```

The PowerShell installer compiles with the local JDK, creates `carbon.cmd`, and
does not download additional dependencies. Source installers preserve existing
configuration and support the same `--setup`/`-Setup` and host selection
options as the recommended package installers.

## 3. Integrate Codex, OpenClaw, or an MCP host

### Recommended: one-command CLI setup

The same command works on macOS, Linux, and Windows after `carbon` is on
`PATH`:

```bash
# Detect installed supported hosts and configure each one once
carbon setup

# Select hosts explicitly
carbon setup --host codex,claude,openclaw

# Preview without changing host configuration
carbon setup --host codex --dry-run

# Inspect and repair an installation
carbon integrations list
carbon doctor
```

PowerShell uses the same arguments; invoke `carbon.cmd` if the installation
directory has not yet been added to `Path`.

The current automatic adapters are:

| Host | Detected executable | Setup scope | Current coverage |
|---|---|---|---|
| OpenAI Codex CLI | `codex` | host user configuration | Control plane only |
| Claude Code | `claude` | user | Control plane only |
| OpenClaw | `openclaw` | host configuration | Control plane only |
| Qoder CLI | `qodercli` | user | Control plane only |
| CodeBuddy / WorkBuddy CLI | `codebuddy` | host configuration | Control plane only |
| Gemini CLI | `gemini` | user | Control plane only |
| GitHub Copilot CLI | `copilot` | host configuration | Control plane only |

Hosts without a stable registration CLI use the guided catalog:

| Target | Command | Result |
|---|---|---|
| Most local stdio MCP hosts | `carbon integrations export generic-stdio --format mcp-json` | Portable command/argument descriptor |
| WorkBuddy desktop | `carbon integrations guide workbuddy-desktop` | UI steps plus an exportable stdio descriptor |
| Coze / 扣子 cloud | `carbon integrations guide coze` | Explicit remote-transport requirement; no unsafe local config is generated |

`carbon setup` registers the dependency-free `carbon mcp serve` control
server under the stable name `carbongate`. It is idempotent: an entry already
managed by CarbonGate is left unchanged. An existing same-name entry not owned
by CarbonGate is reported as a conflict and never overwritten. A newly added
entry is verified, and a failed verification is rolled back. Ownership is
stored in `~/.carbongate/integrations/registry.json` (or the active
`CARBON_HOME`). Remove only an owned entry with:

```bash
carbon integrations remove codex
```

Export commands are read-only and never edit host configuration:

```bash
carbon integrations guide generic-stdio
carbon integrations export generic-stdio --format descriptor
carbon integrations export generic-stdio --format mcp-json
carbon integrations export generic-stdio --format codex-toml
```

After setup, a compatible host can answer requests such as “show CarbonGate
blocked actions”, “list pending CarbonGate approvals”, or “switch CarbonGate
to require approval every time”. The control server exposes status, rules,
blocked events, approval/denial, and natural-language mode tools.

> [!IMPORTANT]
> Automatic setup in this version is `CONTROL_ONLY`. It makes CarbonGate
> query and control tools available to the host; it does not automatically
> intercept that host's built-in shell, file, or network tools. Route an MCP
> server through the proxy below, use `carbon exec`, or integrate the Java/HTTP
> API when enforcement is required.

GUI and cloud products without a stable local MCP registration CLI, including
WorkBuddy desktop and Coze/扣子 environments, use their product's “Add MCP
server” UI and the `carbon mcp serve` command. Their automatic adapters remain
guided until a stable vendor CLI/config contract can be verified.

### Protect an existing MCP server

The recommended MCP enforcement path is a reusable protected route. Create it
once, then export a small descriptor for Codex, OpenClaw, WorkBuddy, or any
other local stdio MCP host:

```bash
carbon mcp profile add filesystem \
  --workspace /absolute/project/path \
  -- npx some-mcp-server
carbon mcp profile list
carbon mcp profile show filesystem
carbon mcp profile export filesystem --format mcp-json
```

On Windows, use the same commands in PowerShell with a Windows workspace path
and the upstream Windows launcher, such as `npx.cmd`. The exported host entry
runs `carbon mcp profile run filesystem`; every upstream `tools/call` therefore
passes through CarbonGate. `descriptor`, `mcp-json`, and `codex-toml` exports
are available, are read-only, and explicitly report `mcp_only` coverage.

Profiles are stored atomically in
`$CARBON_HOME/mcp/profiles.json` (normally
`~/.carbongate/mcp/profiles.json`). The registry is capped at 100 profiles and
1 MiB. It is compact configuration state, not an event log, so it does not
consume the daily 10 MB local log budget. CarbonGate rejects secret-like
command arguments and credential options such as `--token` or `--api-key`;
provide credentials through the upstream process environment instead.

Manage a route without editing host configuration:

```bash
carbon mcp profile export filesystem --format descriptor
carbon mcp profile export filesystem --format codex-toml
carbon mcp profile remove filesystem
```

For temporary or scripted use, CarbonGate can also wrap an existing stdio MCP
server directly. Put the original server command after `--`:

```text
carbon mcp proxy --workspace /absolute/project/path -- ORIGINAL_SERVER [ARGS...]
```

Generic MCP configuration for macOS or Linux:

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

On Windows, use the command launcher and Windows paths:

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

Use this proxy entry in Codex, OpenClaw, or any other stdio MCP host instead of
the original MCP server entry. CarbonGate evaluates `tools/call` before
forwarding it.

Warnings appear in the host-captured `stderr` stream or tool response. When an
operation requires manual approval:

```bash
carbon approvals list
carbon approvals approve <id>
```

The Agent must retry the exact same operation. Approval is consumed once and
expires after 24 hours. Reject a pending request with:

```bash
carbon approvals deny <id>
```

Compatible Agents can also receive CarbonGate connection metadata through:

```bash
carbon run --workspace /path/to/project -- your-agent-command
```

This injects `CARBON_ENDPOINT`, `CARBON_WORKSPACE`, `CARBON_PROFILE`, and
`CARBON_MODE`. It does not stop a child process from bypassing CarbonGate.

## 4. Integrate a Java 21 application

CarbonGate is not currently published to a public Maven repository. Build the
JAR from source:

```bash
./scripts/build.sh
# build/carbongate.jar
```

On Windows, `scripts\install.ps1` also produces `build\carbongate.jar`.

### Gradle

Copy the JAR into the application's `libs/` directory:

```kotlin
dependencies {
    implementation(files("libs/carbongate.jar"))
}
```

### Maven

Install the JAR into the local Maven repository:

```bash
mvn install:install-file \
  -Dfile=/absolute/path/to/carbongate.jar \
  -DgroupId=io.carbongate \
  -DartifactId=carbongate \
  -Dversion=0.2.0 \
  -Dpackaging=jar
```

Add the dependency:

```xml
<dependency>
  <groupId>io.carbongate</groupId>
  <artifactId>carbongate</artifactId>
  <version>0.2.0</version>
</dependency>
```

### Recommended: sidecar gateway

Start CarbonGate separately:

```bash
CARBON_HOME=/var/lib/carbongate \
  carbon gateway --profile strict --port 8765 --workspace /srv/app/workspace
```

Call the gateway from Java:

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
    // Execute the real operation only after ALLOW.
}
```

The gateway listens on `127.0.0.1` and has no remote authentication. Do not
expose it directly to a network.

### In-process gateway

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
    // Execute the real operation.
}
```

Application code must evaluate the action before performing it and proceed only
for `Decision.ALLOW`. `ASK` is not permission.

### Enterprise detailed audit

```java
var runtime = CarbonGateRuntime.enterprise(
        Path.of("/var/lib/carbongate"),
        Path.of("/var/log/company/carbongate"),
        PolicyProfile.STRICT,
        100_000_000L
);
```

Enterprise mode records detailed allow, ask, approval, deny, and internal-error
events. Required audit write failures fail closed and return `DENY`. Implement
`AuditSink` to send events to an existing SIEM, database, or logging platform.

## 5. Configure and operate CarbonGate

### Core commands

```text
carbon status
carbon rules
carbon config init|show|path|set <key> <value>
carbon blocked [--limit 20]
carbon approvals list|approve <id>|deny <id>
carbon mode show|set <natural-language level>
carbon control "natural-language level instruction"
carbon setup [--host HOST[,HOST...]] [--all] [--dry-run]
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

Examples:

```bash
carbon check --workspace /path/to/project -- 'git status'
carbon exec --workspace /path/to/project -- 'touch result.txt'
carbon blocked --limit 20
carbon control "require approval every time"
carbon control "restore balanced mode"
```

### Configuration file

The default file is `~/.carbongate/carbon.conf` on macOS/Linux and
`%USERPROFILE%\.carbongate\carbon.conf` on Windows. Query the active path with:

```bash
carbon config path
```

Complete configuration:

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

| Key | Default | Description |
|---|---:|---|
| `mode` | `BALANCED` | `BALANCED`, `WARN`, `APPROVAL`, or `BLOCK` |
| `rules.shell.enabled` | `true` | Shell command risk rules |
| `rules.filesystem.enabled` | `true` | File boundary and path-risk rules |
| `rules.network.enabled` | `true` | Network egress risk rules |
| `rules.secrets.enabled` | `true` | Sensitive-data risk rules; baseline redaction still applies |
| `audit.mode` | `LOCAL_MINIMAL` | `LOCAL_MINIMAL` or `ENTERPRISE_DETAILED` |
| `audit.local.dailyLimitBytes` | `10000000` | Combined local daily hard cap, maximum 10,000,000 bytes |
| `audit.enterprise.directory` | `enterprise-audit` | Enterprise directory; relative to `CARBON_HOME` |
| `audit.enterprise.dailyLimitBytes` | `100000000` | Enterprise daily safety cap |
| `alerts.consoleDailyLimit` | `100` | Console warning limit for a long-running MCP proxy |

Change a value with:

```bash
carbon config set rules.network.enabled false
carbon config set audit.local.dailyLimitBytes 10000000
carbon config set audit.mode ENTERPRISE_DETAILED
```

Rule and `mode` changes apply to the next Tool Call. Audit mode, directory, and
capacity changes require a restart of long-running gateways or MCP proxies.
Unknown keys and invalid values are rejected.

### Enforcement modes

| Mode | Behavior |
|---|---|
| `BALANCED` | Allow, ask, or deny according to risk |
| `WARN` | Report risk but allow the operation |
| `APPROVAL` | Require one-time human approval for every operation |
| `BLOCK` | Deny all Agent operations |

Natural-language control accepts Chinese or English intent:

```bash
carbon control "switch to warn mode"
carbon control "require approval every time"
carbon control "block all operations"
carbon control "restore balanced mode"
```

`--profile strict|balanced|audit` controls how risk maps to decisions; `mode` is
the global runtime control level.

### Installation health check

`carbon doctor` reports one machine-readable result covering the running Java
version, CarbonGate state directory, configuration, 10 MB local log cap,
control-server invocation, integration registry, and every detected host. It
returns a non-zero status for a broken managed registration, an external
same-name conflict, a missing JAR, an unreadable registry, or another failed
system check. Missing optional hosts are informational.

### Logging and alerts

Local CLI, Codex, and OpenClaw installations default to `LOCAL_MINIMAL`:

- Only complete blocks and internal errors are persisted
- Allow, warning, and pending-approval events are not written
- Blocked and error files share a 10,000,000-byte daily hard cap
- Each record is at most 1,024 bytes and long text is truncated after redaction
- `carbon status` reports the active log paths and current usage

Enterprise Java services can explicitly use `ENTERPRISE_DETAILED` for complete
security-decision and approval auditing. See
[control, approval, and logging](docs/control-and-logging.md).

## Security and development

Read the [threat model](docs/threat-model.md) before treating CarbonGate as a
security boundary. Report suspected vulnerabilities through
[SECURITY.md](SECURITY.md).

Run the complete verification suite:

```bash
./scripts/verify.sh
```

Other development commands:

```bash
./scripts/test.sh
./scripts/build.sh
./scripts/functional-test.sh
./scripts/package.sh 0.2.0
```

Packaging produces `carbongate-0.2.0.tar.gz` and
`carbongate-0.2.0.zip`. `scripts/package-test.sh` verifies that both contain
the platform launchers, package installers, documentation, configuration, and
license notices.

CarbonGate is licensed under the [Apache License 2.0](LICENSE). Distributed
artifacts must follow the [dependency and license policy](docs/dependency-policy.md)
and keep [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) current.
