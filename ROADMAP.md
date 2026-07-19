# CarbonGate roadmap

CarbonGate is a small security backbone with optional adapters and enterprise
components. New work must reduce installation or integration friction without
moving optional behavior into Core.

## Product rules

1. **Core stays small.** No package-manager code, vendor SDK, DLP corpus, or
   container runtime is bundled in the Core JAR.
2. **Adapters translate; they do not reimplement.** Skill, npm, Homebrew, and
   host adapters call the same CLI and consume the same release contract.
3. **Installation is explicit.** No post-install download, default daemon, or
   silent host mutation. Downloaded release artifacts are SHA-256 verified.
4. **Enterprise depth stays optional.** Packs, Providers, and Sandboxes are
   separate processes or packages selected by the operator.
5. **One iteration, one boundary.** Each phase has tests and can ship without
   requiring the next phase.

## Delivery plan

| Phase | Deliverable | Boundary | Status |
|---|---|---|---|
| D0 | Native macOS/Linux/Windows and portable Java 21 releases | Release assets only | Complete in v0.3.0 |
| D1 | Cross-platform bootstrap and canonical release-asset contract | Shell/PowerShell distribution adapter | Complete in Unreleased |
| D2 | `@carbongate/cli` | Zero-runtime-dependency npm adapter; explicit `setup`, no network in `postinstall` | Complete in Unreleased |
| D3 | `soya7700/homebrew-tap` | Formula generated from the same release contract and checksums | Planned |
| D4 | Generic CLI host adapter kit | Small declarative descriptors for Codex, Claude Code, OpenClaw, Qoder, WorkBuddy, Coze, and compatible MCP hosts | Planned |
| E1 | Pack authoring kit and validation | Declarative rules only; no executable Pack code | Planned |
| E2 | Provider SDK and catalog | Out-of-process DLP, approval, and audit modules | Planned |
| E3 | Sandbox profiles | Optional Docker/Podman and future isolation providers | Planned |
| E4 | Enterprise operations | Signed component registry, policy rollout, observability, and HA gateway adapters | Planned |

## Phase acceptance gates

### D1 — bootstrap and release contract

- One command installs the native CLI on macOS Apple Silicon, Linux x64, or
  Windows x64 without Java.
- The bootstrap downloads only an explicit CarbonGate release, verifies its
  `.sha256`, and then invokes the installer already inside that archive.
- Release asset names live in one data-only manifest and are tested against
  packaging automation.
- HTTPS is mandatory by default; an explicit opt-in permits `file://` mirrors
  for offline enterprise installation and tests.

### D2 — npm adapter

- Public package name is `@carbongate/cli` under the `carbongate` npm scope.
- Node built-ins only and no install-time dependency download.
- `npx @carbongate/cli setup` explicitly selects and verifies a GitHub Release,
  then delegates to the same CarbonGate CLI.
- Package publication is manual and tied to a matching GitHub Release version.

### D3 — Homebrew adapter

- Public tap repository is `soya7700/homebrew-tap`.
- The formula consumes published native assets and pinned SHA-256 values; it
  does not build CarbonGate with a different Java baseline.
- Formula updates are generated from the release contract, reviewed, audited,
  and committed in the tap repository.

### D4 and enterprise phases

- Host compatibility is descriptor-first; host-specific executable code is a
  last resort and remains outside Core.
- Enterprise modules implement the existing component protocol and Guard
  Pipeline. They cannot become transitive Core dependencies.
- Custom rule pools follow the Pack schema, size limits, stable identifiers,
  test fixtures, and explicit personal/enterprise classification.

The release version continues to follow Semantic Versioning. A phase may span
multiple patch or minor versions; completion is determined by its acceptance
gates, not by a predetermined version number.
