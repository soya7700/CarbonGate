# CarbonGate MVP threat model

## Assets

- Files outside the explicitly selected workspace
- Credentials, tokens, personal data, and source code
- Network destinations and internal services
- Integrity of the host and audit record

## Trust boundaries

The model, agent, MCP server, tool arguments, downloaded content, and tool output
are untrusted. The CarbonGate process, its policy, and the host isolation backend
are trusted for the MVP.

## Enforced paths

CarbonGate can enforce an action only when the action is evaluated and executed
through its CLI executor, HTTP gateway, Java SDK integration, or MCP proxy. The
MCP proxy rejects dangerous tool calls before forwarding them to the server.

## Known non-goals and limitations

- `carbon run` does not intercept arbitrary syscalls made by its child process.
- Static shell analysis is defense in depth and can have false positives or
  false negatives. Hostile commands require an OS/container sandbox.
- The MVP does not transparently decrypt or inspect arbitrary HTTPS traffic.
- File path checks reduce traversal and symlink escapes but do not replace mount
  namespaces and are not a complete defense against filesystem race conditions.
- The local HTTP API has no remote authentication and binds to loopback only.
- Blocked/error logs are not cryptographically signed. They deliberately omit
  allow, warning, and pending-approval events and stop at 1,000,000 bytes/day.
- The preceding minimal-log guarantee applies to local-agent mode. Explicit
  enterprise audit records all decisions in a separate configured sink and has
  its own capacity and retention obligations.

## Safe deployment

Run untrusted agents in a non-privileged container or OS sandbox. Mount only the
needed workspace, do not expose the Docker socket, block direct egress, and allow
network access only through a controlled proxy. In enterprise deployments, local
project rules must only narrow centrally signed policy.
