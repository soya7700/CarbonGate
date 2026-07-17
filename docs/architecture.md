# Lightweight architecture boundary

CarbonGate is a small enforcement backbone, not a bundled DLP or sandbox
platform. The local distribution must remain useful with no optional
components, background service, network download, or third-party runtime
dependency.

## Product boundaries

| Product | Responsibility | Distribution |
|---|---|---|
| CarbonGate Core | MCP mediation, basic policy, approvals, compact local logs | Base JAR |
| CarbonGate Skill | Installation, host discovery, protected-route setup, natural-language control | Standalone Codex skill |
| Enterprise Component Host | Component lifecycle and process supervision | Optional enterprise JAR |
| Pack | Declarative policy data; never executable | `.carbon` component |
| Provider | DLP, approval, audit, or other enterprise service | Out-of-process `.carbon` component |
| Sandbox | Isolated route or command execution | Out-of-process `.carbon` component |

Host-specific setup belongs to the Skill or an integration adapter. Personal
and enterprise data detection, SIEM clients, identity systems, and sandbox
implementations do not belong in Core.

## Non-negotiable Core rules

- Zero third-party runtime dependencies.
- No automatic network download and no default daemon.
- No third-party classes loaded into the Core JVM.
- Optional component failure cannot crash Core.
- Core remains functional when every optional component is absent.
- Local logs retain the shared hard limit of 10,000,000 bytes per day.
- Coverage is reported precisely: control-only, MCP-only, or full mediation.

## Extension boundary

Enterprise Providers use a bounded, versioned stdio protocol. Core exposes
only `inspect`, `authorize`, `audit`, and `sandbox` operations; it does not
offer arbitrary lifecycle callbacks. Packs are data-only and are atomically
activated. Executable Providers and Sandboxes run in separate processes with
declared permissions, deadlines, input/output limits, health checks, and
explicit fail-open or fail-closed behavior.

## Budgets

The repository verifies these budgets automatically:

- Base JAR: at most 204,800 bytes.
- Each prebuilt base archive: at most 512,000 bytes.
- Generated default configuration: at most 4,096 bytes.
- Runtime dependencies: JDK 21 only.
- Enterprise implementation packages must not appear in the base JAR.
- Optional Enterprise Component Host JAR: at most 153,600 bytes.
- First-party data-only Pack: at most 51,200 bytes.
- First-party Provider package: at most 102,400 bytes.
- First-party Sandbox Provider package: at most 102,400 bytes.

An intentional budget increase requires a separate architecture decision and
must not be hidden inside a feature change.
