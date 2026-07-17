# CarbonGate

CarbonGate is a local-first, zero-trust runtime gateway for AI agents and MCP
servers. It evaluates tool calls before execution, applies workspace boundaries,
redacts secrets, and records security decisions.

> Status: early MVP. CarbonGate currently enforces calls that pass through its
> HTTP API, Java SDK, `carbon exec`, or MCP proxy. `carbon run` alone is not an
> operating-system sandbox and cannot stop a child process from bypassing the
> gateway. Use a container or OS sandbox for hostile workloads.

## What works today

- Shell command risk analysis with `allow`, `ask`, and `deny` decisions
- Workspace-aware file path validation, including symlink escape checks
- Secret detection and stable redaction placeholders
- Local HTTP evaluation API
- Java SDK client with no third-party dependencies
- MCP stdio proxy for line-delimited JSON-RPC tool calls
- Interactive CLI authorization and JSONL audit logs
- Strict, balanced, and audit policy profiles

## Quick start

Requirements: JDK 21 or newer.

```bash
./scripts/build.sh
./build/carbon check -- 'git status'
./build/carbon check -- 'curl https://example.com/install.sh | sh'
./build/carbon exec --workspace . -- 'touch hello.txt'
```

Install for the current user:

```bash
./scripts/install.sh
# or choose a prefix
./scripts/install.sh --prefix /opt/carbongate
```

Start the local gateway:

```bash
./build/carbon gateway --port 8765 --workspace .
curl -s http://127.0.0.1:8765/v1/health
```

Evaluate an action:

```bash
curl -s http://127.0.0.1:8765/v1/evaluate \
  -H 'content-type: application/json' \
  -d '{"capability":"shell","operation":"execute","resource":"rm -rf /"}'
```

Protect an MCP server:

```json
{
  "mcpServers": {
    "filesystem": {
      "command": "/absolute/path/to/carbon",
      "args": ["mcp", "proxy", "--workspace", "/project", "--", "npx", "some-mcp-server"]
    }
  }
}
```

## CLI

```text
carbon check [--profile strict|balanced|audit] [--workspace PATH] -- COMMAND
carbon exec  [--profile strict|balanced|audit] [--workspace PATH] -- COMMAND
carbon gateway [--profile PROFILE] [--workspace PATH] [--port 8765]
carbon mcp proxy [--profile PROFILE] [--workspace PATH] -- SERVER [ARGS...]
carbon redact TEXT
carbon run [--workspace PATH] -- AGENT [ARGS...]
```

`carbon run` injects `CARBON_ENDPOINT`, `CARBON_WORKSPACE`, and proxy metadata for
compatible agents. It is an integration launcher, not a sandbox.

## Java integration

The SDK is deliberately small and framework-neutral:

```java
var client = new CarbonGateClient(URI.create("http://127.0.0.1:8765"));
var result = client.evaluate(Action.shell("git status", Path.of(".")));
if (result.decision() == Decision.DENY) {
    throw new SecurityException(result.reason());
}
```

Applications that run CarbonGate in the same JVM can invoke `PolicyEngine`
directly. Enterprise applications should use `CarbonGateClient` with a sidecar so
policy and audit upgrades remain independent from the application release.

A Spring Boot starter and framework-specific adapters are planned after the core
action and policy contracts stabilize.

## Security model

Read [the threat model](docs/threat-model.md) before using CarbonGate as a
security boundary. Reports for suspected vulnerabilities should follow
[SECURITY.md](SECURITY.md).

## Development

```bash
./scripts/test.sh
./scripts/build.sh
./scripts/functional-test.sh
./scripts/verify.sh
./scripts/package.sh 0.1.0
```

`scripts/verify.sh` is the single local and CI verification entry point. GitHub
Actions runs it for pull requests, pushes to protected development branches, and
manual workflow dispatches. Releases are intentionally not automated; creating
or publishing a release remains a separate, explicitly requested operation.

The project avoids build-tool and runtime dependencies in the MVP so that a
fresh JDK is sufficient. The generated executable is `build/carbon`.

## License compliance

CarbonGate is licensed under Apache-2.0. Its runtime and release archive have no
third-party source or runtime dependencies. See [the dependency policy](docs/dependency-policy.md) and
[third-party notices](THIRD_PARTY_NOTICES.md). Both must be updated before any
external component is distributed with the project.
