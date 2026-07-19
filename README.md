# CarbonGate

Local-first, zero-trust security gateway for AI agents and MCP servers.

[简体中文](README-CN.md)

CarbonGate evaluates commands and MCP tool calls before execution, limits file
and network access, redacts secrets, and supports explicit approval for risky
operations. The default installation stays small; enterprise controls are
independent components that are installed only when needed.

> [!IMPORTANT]
> CarbonGate protects only traffic routed through its CLI, Java API, HTTP
> gateway, or MCP proxy. Host built-in tools are not intercepted automatically.

## Product map

| Product | Purpose | Installed by default |
|---|---|---:|
| **CarbonGate Core** | Command/MCP policy, approvals, compact logs, CLI, Java and HTTP APIs | Yes |
| **CarbonGate Skill** | Natural-language installation, inspection, protection, and control in Codex | With Codex setup |
| **npm Adapter** | Explicit Node.js launcher for a verified native Release | No |
| **Enterprise Component Host** | Lifecycle, process isolation, health checks, and Guard Pipeline | No |
| **Pack** | Declarative rules only; never executable | No |
| **Provider** | DLP inspection, authorization, audit, or enterprise integrations | No |
| **Sandbox** | Container-backed command isolation | No |

The architecture deliberately keeps enterprise code out of the Core JAR. See
[the lightweight architecture boundary](docs/architecture.md) and the
[delivery roadmap](ROADMAP.md).

```text
Local Agent path
  Agent / MCP host -> CarbonGate Skill -> Core -> protected MCP route

Enterprise path
  Application -> Enterprise Guard Pipeline
                 inspect -> authorize -> sandbox (after allow) -> audit
                    |           |             |                    |
                  Pack      Provider       Sandbox              Provider
```

## Choose a path

| Goal | Start here |
|---|---|
| Install for Codex, Claude Code, OpenClaw, or another local CLI | [Recommended one-command installation](#1-recommended-one-command-installation) |
| Protect an MCP server | [Local Agent and MCP integration](#3-local-agent-and-mcp-integration) |
| Query blocks, approvals, rules, or change the level | [Core control and configuration](#4-core-control-and-configuration) |
| Add Pack, DLP, approval, audit, or Sandbox controls | [Enterprise components](#5-enterprise-components) |
| Integrate a Java 21 service | [Java application integration](#6-java-21-application-integration) |
| Build or modify CarbonGate | [Install from source](#2-alternative-install-from-source) |

## Requirements

- No Java runtime for a prebuilt native local installation
- `curl`, `tar`, and `shasum` or `sha256sum` for the macOS/Linux bootstrap
- Node.js 18.17+ only when using the optional npm adapter
- JDK 21 (`java`, `javac`, and `jar`) and Git only for source, JVM, or enterprise builds
- macOS, Linux, or Windows PowerShell 5.1+

Java 21 is the product source, bytecode, JVM runtime, and enterprise integration
baseline. Release automation uses GraalVM Community 25.1.3 only as the current
open-source Native Image builder; the resulting native CLI has no JDK runtime
dependency and does not change CarbonGate's Java 21 compatibility baseline.

Core and first-party components have no third-party source or runtime library
dependencies. The Container Sandbox can invoke a user-installed Docker or
Podman CLI; neither is downloaded or redistributed.

## 1. Recommended: one-command installation

The bootstrap selects the matching native archive from the
[latest GitHub Release](https://github.com/soya7700/CarbonGate/releases/latest),
verifies its published SHA-256 value, and runs the installer inside that
archive. It does not require Java or start a background service.

### macOS and Linux

Apple Silicon macOS and x64 Linux:

```bash
curl -fsSL https://raw.githubusercontent.com/soya7700/CarbonGate/main/scripts/install-release.sh | sh -s -- --setup
```

Configure only selected hosts:

```bash
curl -fsSL https://raw.githubusercontent.com/soya7700/CarbonGate/main/scripts/install-release.sh | sh -s -- --host codex,claude,openclaw
```

The default CLI is `~/.local/bin/carbon`. If needed in the current shell:

```bash
export PATH="$HOME/.local/bin:$PATH"
```

### Windows

Open PowerShell on x64 Windows:

```powershell
& ([scriptblock]::Create((irm 'https://raw.githubusercontent.com/soya7700/CarbonGate/main/scripts/install-release.ps1'))) -Setup
```

Configure only selected hosts:

```powershell
& ([scriptblock]::Create((irm 'https://raw.githubusercontent.com/soya7700/CarbonGate/main/scripts/install-release.ps1'))) -Hosts "codex,claude,openclaw"
```

Use the CLI immediately in the current terminal:

```powershell
$env:Path = "$env:LOCALAPPDATA\CarbonGate\bin;$env:Path"
```

### npm adapter

The optional @carbongate/cli package is published manually only after a
matching GitHub Release exists. Once it is published, Node.js 18.17+ users can
use the same verified release route without installing Java:

```bash
npx @carbongate/cli install
npx @carbongate/cli setup
npx @carbongate/cli setup --host codex,claude,openclaw
```

Installing the npm package performs no download. The explicit install command
downloads the matching native Release, verifies SHA-256, and invokes its bundled
installer without changing Agent hosts; setup additionally configures hosts. See
[the npm adapter contract](docs/npm-adapter.md).

### Installer behavior

The bootstrap downloads only the manifest, selected CarbonGate archive, and
its checksum over HTTPS. The packaged installer then:

- verifies the native executable before installation;
- preserves an existing `carbon.conf`;
- registers each detected host at most once;
- never overwrites an unmanaged same-name `carbongate` entry;
- verifies new registrations and rolls them back on failure;
- installs the bundled CarbonGate Skill for Codex without replacing an
  existing same-name Skill;
- downloads no dependency and starts no background service.

Omit `--setup` or `-Setup` to install without changing host configuration. Use
`--prefix PATH` or `-Prefix PATH` for a custom destination.

| Item | macOS/Linux | Windows |
|---|---|---|
| CLI | `~/.local/bin/carbon` | `%LOCALAPPDATA%\CarbonGate\bin\carbon.exe` |
| Java runtime | Not required | Not required |
| State/config | `~/.carbongate/` | `%USERPROFILE%\.carbongate\` |

Override state on any platform with `CARBON_HOME`.

Pin a release with `--version 0.3.1` on macOS/Linux or `-Version 0.3.1` on
Windows. Offline mirrors can override `CARBONGATE_MANIFEST_URL` and
`CARBONGATE_RELEASE_BASE_URL`; `file://` sources additionally require the
explicit `CARBONGATE_ALLOW_FILE_URLS=1` opt-in.

### Manual verified fallback

If your environment does not allow a downloaded script to run directly,
download and inspect the bootstrap first, or download the archive and
`SHA256SUMS` from the latest Release yourself. After verification and
extraction, run:

```bash
./carbongate-VERSION-PLATFORM/install.sh --setup
```

```powershell
.\carbongate-VERSION-windows-x64\install.ps1 -Setup
```

The portable `carbongate-VERSION.tar.gz` and `.zip` JAR archives remain
available for Java 21 environments, macOS Intel, and enterprise evaluation.

### Verify

```bash
carbon version
carbon doctor
carbon status
carbon rules
```

## 2. Alternative: install from source

Use this path for development, branch testing, or enterprise component builds.
It requires a full JDK 21 toolchain.

### macOS and Linux

```bash
git clone https://github.com/soya7700/CarbonGate.git
cd CarbonGate
./scripts/install.sh --setup
export PATH="$HOME/.local/bin:$PATH"
carbon doctor
```

Select hosts with `--host codex,claude` or choose another destination with
`--prefix /opt/carbongate`.

### Windows

```powershell
git clone https://github.com/soya7700/CarbonGate.git
Set-Location CarbonGate
powershell -ExecutionPolicy Bypass -File .\scripts\install.ps1 -Setup
$env:Path = "$env:LOCALAPPDATA\CarbonGate\bin;$env:Path"
carbon doctor
```

Use `-Hosts "codex,claude"` or `-Prefix "C:\Tools\CarbonGate"` as needed.
Source installers compile locally, preserve existing configuration, and do not
download build dependencies.

## 3. Local Agent and MCP integration

### CarbonGate Skill

When Codex is selected during setup, the bundled
[CarbonGate Skill](skills/carbongate/SKILL.md) enables natural-language tasks
such as:

- “Show the latest blocked CarbonGate operations.”
- “List pending approvals and active rules.”
- “Switch CarbonGate to approval mode.”
- “Protect this MCP server for my project.”

The Skill calls the local `carbon` CLI and reports actual coverage. It does not
claim that a control-only connection protects host built-in tools.

### Set up supported hosts

The same commands work on macOS, Linux, and Windows:

```bash
carbon setup
carbon setup --host codex,claude,openclaw
carbon setup --host codex --dry-run
carbon integrations list
carbon doctor
```

Automatic adapters currently cover Codex CLI, Claude Code, OpenClaw, Qoder,
CodeBuddy/WorkBuddy CLI, Gemini CLI, and GitHub Copilot CLI. Guided descriptors
are available for generic stdio MCP hosts, WorkBuddy desktop, and Coze/扣子:

```bash
carbon integrations guide generic-stdio
carbon integrations export generic-stdio --format mcp-json
carbon integrations guide coze
```

Initial host setup is `CONTROL_ONLY`: it exposes status, rules, blocked events,
approvals, and mode controls. Use a protected route for enforcement.

### Protect an MCP server

The Skill's recommended atomic workflow is:

```bash
carbon protect /absolute/project/path --name filesystem --host codex -- npx some-mcp-server
carbon protections
carbon unprotect filesystem --host codex
```

Use `--host generic` to store a protected route and receive a portable
descriptor without claiming to modify an unsupported host.

The lower-level reusable profile workflow is:

```bash
carbon mcp profile add filesystem \
  --workspace /absolute/project/path \
  -- npx some-mcp-server
carbon mcp profile show filesystem
carbon mcp profile export filesystem --format mcp-json
```

On Windows use a Windows workspace path and launchers such as `npx.cmd`.
Exported entries run `carbon mcp profile run filesystem`, so upstream
`tools/call` requests pass through CarbonGate and report `mcp_only` coverage.

Profiles are written atomically to `$CARBON_HOME/mcp/profiles.json`, capped at
100 profiles and 1 MiB, and do not consume the daily event-log budget. Secret
arguments such as `--token` and `--api-key` are rejected; supply credentials
through the upstream process environment.

For temporary use:

```text
carbon mcp proxy --workspace /absolute/project/path -- ORIGINAL_SERVER [ARGS...]
```

`carbon run --workspace /project -- your-agent` only provides CarbonGate
connection metadata. It is not an operating-system sandbox and cannot stop a
child process from bypassing CarbonGate.

## 4. Core control and configuration

### Everyday commands

```bash
carbon status
carbon rules
carbon blocked --limit 20
carbon approvals list
carbon approvals approve <id>
carbon approvals deny <id>
carbon control "require approval every time"
carbon control "restore balanced mode"
carbon doctor
```

An approved operation must be retried exactly; approval is single-use and
expires after 24 hours.

### Enforcement modes

| Mode | Behavior |
|---|---|
| `BALANCED` | Risk-based allow, ask, or deny |
| `WARN` | Warn but allow |
| `APPROVAL` | Ask for every operation |
| `BLOCK` | Deny every Agent operation |

Natural-language mode control accepts English or Chinese intent.

### Configuration

The default file is `~/.carbongate/carbon.conf` or
`%USERPROFILE%\.carbongate\carbon.conf`:

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

Manage it through validated commands:

```bash
carbon config path
carbon config show
carbon config set rules.network.enabled false
carbon config set audit.local.dailyLimitBytes 10000000
```

### Local logging

Local CLI and Agent installations default to `LOCAL_MINIMAL`:

- only complete blocks and internal errors are persisted;
- allow, warning, and pending-approval events are not written;
- blocked and error records share a hard 10,000,000-byte daily limit;
- every compact record is at most 1,024 bytes and is redacted before writing.

See [control, approval, and logging](docs/control-and-logging.md).

## 5. Enterprise components

Enterprise products are optional and built separately from Core. They use the
bounded stdio component protocol and run outside the Core JVM.

### Component catalog

| Component | Type | Function | Documentation |
|---|---|---|---|
| Sensitive Data Baseline | Pack | Personal identity/contact/family and enterprise finance/member-asset rules | [Rule Packs](docs/rule-packs.md) |
| Sensitive Data Provider | Provider / `inspect` | DLP findings without returning matched content | [DLP Provider](docs/sensitive-data-provider.md) |
| Approval Policy Provider | Provider / `authorize` | Stable `allow`, `ask`, and `deny` policy | [Approval Provider](docs/approval-provider.md) |
| Container Sandbox | Sandbox / `sandbox` | Docker/Podman default-deny execution | [Container Sandbox](docs/container-sandbox.md) |
| Enterprise Audit Provider | Provider / `audit` | Sanitized, bounded, SHA-256 chained JSONL audit | [Audit Provider](docs/enterprise-audit-provider.md) |

The lifecycle and protocol are documented in
[Enterprise Component Host](docs/enterprise-components.md).

### Build

macOS and Linux:

```bash
./scripts/build-enterprise.sh
./scripts/build-pack.sh
./scripts/build-provider.sh
./scripts/build-approval.sh
./scripts/build-audit.sh
./scripts/build-sandbox.sh
```

Windows PowerShell uses the matching `.ps1` scripts:

```powershell
.\scripts\build-enterprise.ps1
.\scripts\build-pack.ps1
.\scripts\build-provider.ps1
.\scripts\build-approval.ps1
.\scripts\build-audit.ps1
.\scripts\build-sandbox.ps1
```

### Install and enable

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

Install the Container Sandbox separately and enable it only when Docker or
Podman is available. It requires a locally available digest-pinned image,
disables pulling and networking, and uses a read-only workspace by default.

### Guard Pipeline

The explicit pipeline is:

```text
inspect -> authorize -> sandbox (only after allow) -> audit
```

```bash
./build/carbon-enterprise guard \
  '{"action":"read","risk":"low","content":"text to inspect"}'
```

`fail_closed` changes the result to `deny`; `fail_open` remains visible as a
failed step. Audit receives only a generated event ID, action, risk, decision,
and component names. See [Enterprise Guard Pipeline](docs/enterprise-pipeline.md).

### Custom Packs

Pack rules are declarative and cannot execute code or supply arbitrary regular
expressions. A simple keyword rule looks like:

```json
{
  "id": "company.internal-label",
  "audience": "enterprise",
  "category": "enterprise.internal",
  "severity": "high",
  "match": {"type": "keywords", "terms": ["internal-only"]}
}
```

Fixed detectors include `email`, `phone_cn`, `id_cn`, `bank_card`, and
`api_secret`.

### Component signing

Use JDK 21's Ed25519 implementation to authenticate publishers:

```bash
./build/carbon-enterprise trust generate company-release /secure/keys
./build/carbon-enterprise trust add company-release /secure/keys/company-release.public.x509
./build/carbon-enterprise package source component.carbon \
  --sign company-release /secure/keys/company-release.private.pk8
./build/carbon-enterprise trust policy require_signed
```

Private keys are never packaged. Details are in
[component signing and trust](docs/component-trust.md).

## 6. Java 21 application integration

Build `build/carbongate.jar` with `./scripts/build.sh`. CarbonGate is not yet
published to a public Maven repository; use the JAR directly or install it into
your approved internal repository.

### Recommended sidecar

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

The gateway binds to `127.0.0.1` and has no remote authentication. Never expose
it directly to a network.

### In-process

```java
var runtime = CarbonGateRuntime.fromConfig(
        Path.of("/var/lib/carbongate"), PolicyProfile.STRICT);
var result = runtime.guard().evaluate(
        Action.shell("git status", Path.of("/srv/app/workspace")));
if (result.decision() == Decision.ALLOW) {
    // Execute the real operation.
}
```

Application code must evaluate before execution. `ASK` is not permission.
Enterprise Java services can use `CarbonGateRuntime.enterprise(...)` or a
custom `AuditSink`; this is separate from the optional component pipeline.

## 7. Security, verification, and license

CarbonGate is an early-stage security project, not transparent syscall
interception. Core file checks do not create a mount namespace or Chroot.
Hostile workloads still require a correctly configured container or operating
system sandbox.

Read the [threat model](docs/threat-model.md) and report vulnerabilities through
[SECURITY.md](SECURITY.md).

Run the complete verification suite:

```bash
./scripts/verify.sh
```

It compiles with `--release 21 -Xlint:all -Werror`, tests Core and every optional
component, validates installers and packages, checks size budgets, and rejects
undeclared dependencies.

CarbonGate is licensed under the [Apache License 2.0](LICENSE). Contributions
and distributed artifacts must follow the
[dependency and license policy](docs/dependency-policy.md) and keep
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) current.
