# Query, control, approval, and logging

CarbonGate exposes machine-readable CLI commands so an agent such as Codex can
answer natural-language security questions by invoking the local `carbon`
executable.

## Query commands

```bash
carbon status
carbon rules
carbon blocked --limit 20
carbon approvals list
```

`status` reports the active mode, notification locations, pending approval
count, today's blocked/error counts, bytes written, the hard daily limit, and
the exact local paths. `rules` reports the mode behavior and the shell,
filesystem, network, and secret-handling rule groups. `blocked` returns only
fully denied events; sensitive values are redacted before persistence.

## Natural-language control

```bash
carbon control "切换到警告提醒"
carbon control "以后每次都要手动授权"
carbon control "完全拦截所有操作"
carbon control "恢复默认平衡模式"
```

The parser intentionally accepts only an unambiguous instruction. It rejects
unclear or conflicting text instead of guessing. The setting is stored in
`$CARBON_HOME/carbon.conf` (default `~/.carbongate/carbon.conf`). Running
gateways read modes and rule switches for every action, so those changes apply
to the next tool call.

| Mode | Behavior |
|---|---|
| `balanced` | Risk-based allow, approval, or deny |
| `warn` | Allow and show risk warnings |
| `approval` | Require one-time approval for every action |
| `block` | Deny every action |

## Configuration file

Create and inspect the configuration with:

```bash
carbon config init
carbon config show
carbon config path
carbon config set rules.network.enabled false
```

The generated UTF-8 file contains:

```properties
mode=BALANCED
rules.shell.enabled=true
rules.filesystem.enabled=true
rules.network.enabled=true
rules.secrets.enabled=true
audit.mode=LOCAL_MINIMAL
audit.local.dailyLimitBytes=1000000
audit.enterprise.directory=enterprise-audit
audit.enterprise.dailyLimitBytes=100000000
alerts.consoleDailyLimit=100
```

Unknown keys and invalid values are rejected. The local daily limit can be
reduced but cannot be configured above 1,000,000 bytes. Mode and rule changes
are live; audit sink, directory, and capacity changes require a Gateway restart.

## Manual approval

HTTP and MCP responses return an `approvalId` for an `ask` decision. Review and
approve it with:

```bash
carbon approvals list
carbon approvals approve <id>
```

The agent must retry the same action. Approval is consumed once and is not a
permanent rule. Pending and approved state expires after 24 hours, and at most
100 pending requests are retained. These are small authorization state files,
not append-only logs.

## Where messages appear

- Warnings: terminal `stderr`, or the HTTP/MCP tool response.
- Interactive CLI approval: the controlling terminal.
- Non-interactive approval: `carbon approvals list` and the returned
  `approvalId`.
- Complete blocks: the tool response plus the daily blocked file reported by
  `carbon status`.
- Internal errors: the daily error file reported by `carbon status`.

MCP warning output is limited to 100 messages per process per day, followed by
one suppression notice. This prevents host-captured console output from turning
into a log flood.

## Local-agent disk-write guarantee

CarbonGate never writes `allow`, warning, or `ask` decisions to log files. Only
fully blocked actions and internal errors are written, both at `ERROR` level.
They use daily JSONL files and share one locked, hard budget of 1,000,000 bytes
per local day. A complete JSON event is dropped if appending it would exceed the
budget; files are never partially appended. Individual events are bounded to
4,096 bytes and resources/messages are truncated after redaction.

## Enterprise Java audit

Enterprise services explicitly select `ENTERPRISE_DETAILED`. This records every
allow, warning, pending approval, one-time approval, denial, and internal error
with actor, capability, operation, workspace, sanitized context, decision, risk,
reason, and findings. It uses a separate configurable daily JSONL file and a
100,000,000-byte default safety cap.

```java
var runtime = CarbonGateRuntime.enterprise(
    Path.of("/var/lib/carbongate"),
    Path.of("/var/log/company/carbongate"),
    PolicyProfile.STRICT,
    100_000_000L
);
var gateway = new CarbonGateway(8765, workspace, runtime.guard());
```

Alternatively, set `audit.mode=ENTERPRISE_DETAILED` and construct the runtime
with `CarbonGateRuntime.fromConfig(...)`. Enterprise audit is never the default
for local Codex, OpenClaw, or CLI installation.

`AuditSink` is the stable Java boundary for companies that want to send the same
detailed events to an existing SIEM, database, or logging pipeline instead of
using the built-in JSONL sink. A custom implementation must redact secrets and
enforce its own retention and capacity policy.

Enterprise audit is fail-closed: when a required detailed decision event cannot
be written (including when its configured cap is exhausted), CarbonGate returns
`deny` instead of executing an unaudited action. Local minimal mode continues to
enforce a block even if its already-full 1 MB log cannot accept another event.
