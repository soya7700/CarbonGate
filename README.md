# CarbonGate

Local-first zero-trust security gateway for AI agents and MCP servers.

[简体中文](README-CN.md)

> **MVP security boundary**
> CarbonGate can enforce only commands and tool calls that pass through its CLI,
> HTTP gateway, Java API, or MCP proxy. `carbon run` injects integration metadata
> but is not an operating-system sandbox. Use containers or an OS sandbox for
> hostile workloads.

## Overview

CarbonGate evaluates Agent actions before execution and provides:

- Static Shell command risk analysis with `allow`, `ask`, and `deny` decisions
- Workspace-aware file boundary checks, including traversal and symlink escapes
- Network egress risk analysis and sensitive-data leak detection
- Password, token, API key, and private-data redaction
- An MCP stdio proxy, loopback HTTP gateway, and dependency-free Java 21 API
- Natural-language switching among warn, approval, block, and balanced modes
- A one-time approval queue and blocked-event queries
- Compact local-Agent logs and detailed enterprise Java audit modes

This is an early MVP. Transparent syscall interception and real mount-namespace
or Chroot filesystem virtualization are not implemented yet.

## Requirements

- JDK 21 or newer
- macOS, Linux, or another environment capable of running JDK 21 and shell scripts
- Git when installing from source

The CarbonGate core and release archive currently have no third-party source or
runtime dependencies.

## CLI installation

### Install for the current user from source

```bash
git clone https://github.com/soya7700/CarbonGate.git
cd CarbonGate
./scripts/install.sh
```

Default locations:

- Command: `~/.local/bin/carbon`
- JAR: `~/.local/lib/carbongate/carbongate.jar`
- Configuration and runtime state: `~/.carbongate/`

Add the command directory to `PATH` when necessary:

```bash
export PATH="$HOME/.local/bin:$PATH"
```

Choose a different installation prefix with:

```bash
./scripts/install.sh --prefix /opt/carbongate
```

The installer builds with `javac --release 21` and creates
`$CARBON_HOME/carbon.conf` only when it does not already exist.

### Run directly from the source tree

```bash
./scripts/build.sh
./build/carbon version
./build/carbon status
```

### Run from an archive

After receiving or manually creating an archive:

```bash
./scripts/package.sh 0.2.0
tar -xzf build/carbongate-0.2.0.tar.gz
./build/carbongate-0.2.0/bin/carbon version
```

The project does not publish GitHub Releases automatically. A maintainer must
explicitly request and perform each release.

### Verify the installation

```bash
carbon version
carbon config init
carbon status
carbon rules
```

## CLI usage

Evaluate a command without executing it:

```bash
carbon check --workspace /path/to/project -- 'git status'
carbon check --workspace /path/to/project -- 'rm -rf /'
```

Evaluate and then execute; an `ask` result requires interactive or one-time
approval:

```bash
carbon exec --workspace /path/to/project -- 'touch result.txt'
```

Start the loopback-only HTTP gateway:

```bash
carbon gateway --port 8765 --workspace /path/to/project
curl -s http://127.0.0.1:8765/v1/health
```

Evaluate an action over HTTP:

```bash
curl -s http://127.0.0.1:8765/v1/evaluate \
  -H 'content-type: application/json' \
  -d '{"capability":"shell","operation":"execute","resource":"rm -rf /"}'
```

Query and control CarbonGate:

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

Natural-language mode changes accept Chinese or English intent:

```bash
carbon control "switch to warn mode"
carbon control "require approval every time"
carbon control "block all operations"
carbon control "restore balanced mode"
```

Command reference:

```text
carbon status
carbon rules
carbon config init|show|path|set <key> <value>
carbon blocked [--limit 20]
carbon approvals list|approve <id>|deny <id>
carbon mode show|set <natural-language level>
carbon control "natural-language level instruction"
carbon check [--profile strict|balanced|audit] [--workspace PATH] -- COMMAND
carbon exec [--profile strict|balanced|audit] [--workspace PATH] -- COMMAND
carbon gateway [--profile PROFILE] [--workspace PATH] [--port 8765]
carbon mcp proxy [--profile PROFILE] [--workspace PATH] -- SERVER [ARGS...]
carbon redact TEXT
carbon run [--workspace PATH] -- AGENT [ARGS...]
carbon version
```

## MCP, Codex, and OpenClaw integration

Place the original MCP server command after `--` in `carbon mcp proxy`. Generic
stdio MCP configuration:

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

Replace the original MCP server entry in Codex, OpenClaw, or any other stdio
MCP host with this proxy form. CarbonGate evaluates `tools/call` before
forwarding it. Warnings appear in host-captured `stderr` or the tool response.
For manual approval:

```bash
carbon approvals list
carbon approvals approve <id>
```

The Agent must retry the exact same action. Approval is consumed once and
expires after 24 hours.

Compatible Agents can also be launched with injected `CARBON_ENDPOINT`,
`CARBON_WORKSPACE`, `CARBON_PROFILE`, and `CARBON_MODE` metadata:

```bash
carbon run --workspace /path/to/project -- your-agent-command
```

`carbon run` cannot prevent its child process from bypassing CarbonGate and
accessing the system directly.

## Java 21 integration

CarbonGate is not yet published to Maven Central. Build the JAR first:

```bash
./scripts/build.sh
# produces build/carbongate.jar
```

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

Then add:

```xml
<dependency>
  <groupId>io.carbongate</groupId>
  <artifactId>carbongate</artifactId>
  <version>0.2.0</version>
</dependency>
```

### Recommended: sidecar/separate gateway

Start CarbonGate separately:

```bash
CARBON_HOME=/var/lib/carbongate \
  carbon gateway --profile strict --port 8765 --workspace /srv/application/workspace
```

Call it from the Java application:

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
    // Execute the real operation only after ALLOW.
}
```

The sidecar model allows policy and auditing to be upgraded independently. The
current gateway listens only on `127.0.0.1`, but it has no remote
authentication and must not be exposed directly to a network.

### In-process mode

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
    // Execute the real operation.
}
```

Application code must call `guard().evaluate(...)` before performing the real
operation and proceed only for `Decision.ALLOW`. `ASK` is not permission.

### Detailed enterprise audit

```java
var runtime = CarbonGateRuntime.enterprise(
        Path.of("/var/lib/carbongate"),
        Path.of("/var/log/company/carbongate"),
        PolicyProfile.STRICT,
        100_000_000L
);
```

Enterprise mode records detailed allow, ask, approval, deny, and internal-error
events. Required audit write failures fail closed and return `DENY`. Enterprises
can also implement `AuditSink` to send events to an existing SIEM, database, or
logging platform.

A Spring Boot starter and framework-specific adapters are planned after the
core API stabilizes.

## Configuration

Configuration location:

- Default: `~/.carbongate/carbon.conf`
- Custom: set `CARBON_HOME=/custom/state/directory`
- Query: `carbon config path`

Create the default configuration:

```bash
carbon config init
```

Complete example:

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
| `mode` | `BALANCED` | Global enforcement: `BALANCED`, `WARN`, `APPROVAL`, or `BLOCK` |
| `rules.shell.enabled` | `true` | Shell risk rules |
| `rules.filesystem.enabled` | `true` | Filesystem boundary and path-risk rules |
| `rules.network.enabled` | `true` | Network egress risk rules |
| `rules.secrets.enabled` | `true` | Sensitive-data risk rules; baseline output redaction still applies |
| `audit.mode` | `LOCAL_MINIMAL` | `LOCAL_MINIMAL` or `ENTERPRISE_DETAILED` |
| `audit.local.dailyLimitBytes` | `10000000` | Combined local daily hard cap; maximum 10,000,000 bytes |
| `audit.enterprise.directory` | `enterprise-audit` | Enterprise audit directory; relative paths use `CARBON_HOME` |
| `audit.enterprise.dailyLimitBytes` | `100000000` | Enterprise daily safety cap |
| `alerts.consoleDailyLimit` | `100` | Daily console-warning limit for a long-running MCP proxy |

Change values from the CLI:

```bash
carbon config set rules.network.enabled false
carbon config set audit.local.dailyLimitBytes 10000000
carbon config set audit.mode ENTERPRISE_DETAILED
```

Rule and `mode` changes apply to the next Tool Call. Audit mode, directory, and
capacity changes require a restart of long-running gateways or MCP proxies.
Unknown keys and invalid values are rejected. Disabling a rule reduces
protection and should receive security review.

### Enforcement modes

| Mode | Behavior |
|---|---|
| `BALANCED` | Automatically allow, ask, or deny according to risk |
| `WARN` | Report risk but allow the operation |
| `APPROVAL` | Require one-time human approval for every operation |
| `BLOCK` | Deny all Agent operations |

`--profile strict|balanced|audit` controls the mapping from risk to decisions;
`mode` is the global runtime control level.

## Logging and alerts

Local Codex, OpenClaw, and CLI installations default to `LOCAL_MINIMAL`:

- Only complete blocks and internal errors are persisted; allow, warning, and
  pending-approval events are not written
- Blocked and error files share a 10,000,000-byte daily hard cap
- Each record is at most 1,024 bytes and long text is truncated after redaction
- Run `carbon status` to locate the active files

Enterprise Java services can explicitly enable `ENTERPRISE_DETAILED` for full
security-decision and approval auditing. See
[control, approval, and logging](docs/control-and-logging.md) for details.

## Development, testing, and license

```bash
./scripts/test.sh
./scripts/build.sh
./scripts/functional-test.sh
./scripts/verify.sh
./scripts/package.sh 0.2.0
```

`scripts/verify.sh` is the shared local and CI verification entry point. It
includes JDK 21 compilation, unit tests, functional tests, and dependency/license
checks.

CarbonGate is licensed under the [Apache License 2.0](LICENSE). Every release
must follow the [dependency and license policy](docs/dependency-policy.md) and
keep [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) current. Read the
[threat model](docs/threat-model.md) for limitations and [SECURITY.md](SECURITY.md)
for vulnerability reporting.
