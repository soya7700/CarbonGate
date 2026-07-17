---
name: carbongate
description: Install, inspect, configure, protect, and remove CarbonGate routes for Codex or generic stdio MCP hosts. Use when a user asks to install CarbonGate, protect an AI agent project or MCP server, check blocked actions or approvals, change the security level, diagnose an installation, or remove CarbonGate safely.
---

# CarbonGate

Use the local `carbon` CLI as the source of truth. Do not reproduce policy
decisions in the Skill.

## Start safely

1. Run `carbon version` and `carbon doctor` when the command exists.
2. If it is absent, prefer an extracted signed/prebuilt package supplied by the
   user. In a CarbonGate source checkout, use the platform installer from
   `scripts/`. Ask before cloning, downloading, or modifying host configuration.
3. Never download from an unversioned URL and never place tokens, passwords, or
   API keys in an MCP command line.

## Protect a route

1. Resolve the workspace to an existing absolute path.
2. Inspect the host's MCP list and identify the exact upstream server command.
   Ask one concise question if the upstream command or intended server is
   ambiguous.
3. Preview when useful:

   ```bash
   carbon protect /absolute/workspace --name NAME --host codex --dry-run -- SERVER ARGS
   ```

4. Apply and verify:

   ```bash
   carbon protect /absolute/workspace --name NAME --host codex -- SERVER ARGS
   carbon protections
   carbon doctor
   ```

For a generic stdio MCP host, use `--host generic`. Return the emitted
descriptor to the user; do not claim that CarbonGate edited the host.

State clearly that a protected route has `mcp_only` coverage. It mediates the
upstream MCP `tools/call` traffic, not the host's built-in shell, filesystem, or
network tools. Do not describe `carbon run` as an operating-system sandbox.

## Query and control

Use machine-readable commands:

```bash
carbon status
carbon rules
carbon blocked --limit 20
carbon approvals list
carbon control "切换到每次授权"
```

Summarize results without exposing redacted values. Approval is one-time and
the Agent must retry the same operation after approval.

## Remove safely

List managed routes before removal, then remove only CarbonGate-owned state:

```bash
carbon protections
carbon unprotect NAME --host codex
carbon doctor
```

For generic descriptors use `--host generic`. Never delete or overwrite an
unmanaged same-name host entry.
