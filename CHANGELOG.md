# Changelog

All notable changes to CarbonGate are documented here. Version numbers follow
[Semantic Versioning](https://semver.org/), and Git tags use the `vX.Y.Z` form.

## [Unreleased]

### Added

- Canonical, data-only release asset contract shared by future distribution adapters.
- SHA-256-verifying one-command bootstrap installers for macOS, Linux, and Windows.
- Explicit offline mirror support and cross-platform distribution tests.
- English and Chinese lightweight delivery roadmaps for npm, Homebrew, host adapters,
  and optional enterprise components.

## [0.3.0] - 2026-07-18

### Added

- Lightweight Core, control CLI, MCP control server, and protection Skill.
- Command, filesystem, network, secret, approval, and minimal local audit rules.
- Optional enterprise Pack, Provider, approval, audit, and container sandbox modules.
- Signed enterprise component packages and a bounded provider protocol.
- GraalVM Community native CLI builds for macOS Apple Silicon, Linux x64, and
  Windows x64 without a user-installed Java runtime; portable Java 21 archives
  cover macOS Intel and other JVM environments.
- Manual, multi-platform GitHub Release workflow with SHA-256 checksums.

### Security

- Local audit files use a strict 10 MB daily cap and record only blocked/error events.
- Enterprise audit mode remains opt-in and preserves detailed decision records.
