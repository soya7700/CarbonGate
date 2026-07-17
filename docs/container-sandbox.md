# Container Sandbox Provider v1

The optional Container Sandbox Provider executes commands through a
user-installed Docker or Podman CLI. It is separate from CarbonGate Core and is
the first component in the Sandbox product line.

## Security profile

Every invocation uses a fixed fail-closed profile:

- Linux container image pinned by `sha256` digest; mutable tags are rejected
- `--pull=never` and `--network=none`
- read-only container root and read-only workspace by default
- all Linux capabilities dropped and `no-new-privileges` enabled
- numeric unprivileged user, bounded process count, memory, CPU, and timeout
- bounded standard output and error; no shell command string construction
- forced cleanup by a generated container name when execution times out
- no host environment variables, credentials, or container socket mounted

Writable workspace access requires an explicit `"writable": true`. v1 never
allows container networking. A workspace root or path containing a comma is
rejected to prevent overly broad or ambiguous mounts.

## Build and install

macOS and Linux:

```bash
./scripts/build-sandbox.sh
./build/carbon-enterprise install build/container-sandbox-1.0.0.carbon
./build/carbon-enterprise enable container-sandbox 1.0.0
```

Windows PowerShell with Linux containers:

```powershell
.\scripts\build-sandbox.ps1
.\build\carbon-enterprise.cmd install build\container-sandbox-1.0.0.carbon
.\build\carbon-enterprise.cmd enable container-sandbox 1.0.0
```

Enable performs a health check. If neither Docker nor Podman is available, the
component remains disabled.

## Invoke

The image must already exist locally because pulling and network access are
disabled:

```bash
./build/carbon-enterprise invoke container-sandbox sandbox \
  '{"workspace":"/absolute/project","image":"registry/tool@sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef","command":["tool","check"],"timeoutMillis":10000,"memoryMb":256,"cpus":1.0}'
```

This component relies on the isolation guarantees and configuration of the
user's Docker or Podman installation. Those programs are not downloaded,
bundled, or redistributed by CarbonGate and retain their own licenses and
security lifecycle.
