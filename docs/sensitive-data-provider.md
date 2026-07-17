# Sensitive Data Provider v1

The optional Sensitive Data Provider is CarbonGate's first executable
enterprise Provider. It runs out of process, reads only validated and enabled
Packs, and never returns matched content.

## Build and enable

macOS and Linux:

```bash
./scripts/build-pack.sh
./scripts/build-provider.sh
```

Windows PowerShell:

```powershell
.\scripts\build-pack.ps1
.\scripts\build-provider.ps1
```

Install and enable the Pack before the Provider:

```bash
./build/carbon-enterprise install build/sensitive-data-baseline-1.0.0.carbon
./build/carbon-enterprise enable sensitive-data-baseline 1.0.0
./build/carbon-enterprise install build/sensitive-data-provider-1.0.0.carbon
./build/carbon-enterprise enable sensitive-data-provider 1.0.0
```

Inspect text:

```bash
./build/carbon-enterprise invoke sensitive-data-provider inspect \
  '{"text":"content to inspect"}'
```

The result contains `allow`, `warn`, `review`, or `block`, plus rule IDs,
categories, audiences, severities, and counts. It deliberately contains no
matched snippets or original content.

## Permission and trust boundary

The Provider declares `packs.read`. Only for that explicit permission, the host
injects validated active Pack documents into a reserved `_carbongate` context.
Caller-supplied `_carbongate` data is rejected. No filesystem path, environment
secret, or Core callback is exposed to the Provider.

The fixed detectors are implemented by first-party Java 21 code. Custom Packs
can select fixed detectors or literal keywords, but cannot inject regular
expressions or executable logic. The Provider process remains optional and is
not included in the Core archive.
