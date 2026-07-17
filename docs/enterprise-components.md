# Enterprise Component Host v1

The optional Enterprise Component Host is built separately from CarbonGate
Core. The repository provides independently packaged DLP, approval, audit, and
container Sandbox components; none of them is bundled into the lightweight
Core archive or enabled by default.

## Build and verify

```bash
./scripts/build.sh
./scripts/build-enterprise.sh
./scripts/enterprise-test.sh
./build/carbon-enterprise version
```

On Windows, run `scripts\build-enterprise.ps1` after the base JAR is built,
then use `build\carbon-enterprise.cmd`.

The base `carbongate.jar` never contains `io.carbongate.enterprise` classes.
The optional host uses the base JAR only for stable model and JSON utilities.

## Package format

A `.carbon` file is a ZIP-compatible archive:

```text
component.carbon
├── manifest.json
├── checksums.json
├── LICENSE
├── NOTICE
└── payload/
```

Example provider manifest:

```json
{
  "apiVersion": "carbongate.io/v1",
  "kind": "provider",
  "metadata": {"id": "enterprise-audit", "version": "1.0.0"},
  "spec": {
    "entrypoint": ["java", "-jar", "${componentDir}/payload/provider.jar"],
    "operations": ["audit"],
    "permissions": ["data.sanitized", "filesystem.write"],
    "timeoutMillis": 2000,
    "failureMode": "fail_closed"
  },
  "license": {"spdx": "Apache-2.0"}
}
```

`checksums.json` must use SHA-256 and name every regular file under `payload/`
exactly once. Installation rejects missing license artifacts, duplicate
versions, path traversal, oversized archives, undeclared payloads, and checksum
mismatches. A Pack must not declare an entrypoint or executable operation.

Checksums detect accidental or post-build payload changes. Optional Ed25519
signatures authenticate a key in the local publisher trust store; see
[component signing and trust policy](component-trust.md). Review every declared
entrypoint and permission before trusting a publisher key.

## Lifecycle

```bash
./build/carbon-enterprise install provider.carbon
./build/carbon-enterprise list
./build/carbon-enterprise enable enterprise-audit 1.0.0
./build/carbon-enterprise doctor
./build/carbon-enterprise rollback enterprise-audit 0.9.0
./build/carbon-enterprise disable enterprise-audit
./build/carbon-enterprise remove enterprise-audit 1.0.0
```

Install is staged and atomically moved into the component store. Enable first
runs the Provider health operation and only then atomically switches the active
version. Enabling an older installed version is the rollback operation. Active
versions cannot be removed.

## Provider protocol

An executable component receives exactly one bounded JSON line on standard
input and returns exactly one bounded JSON line on standard output. v1 starts a
fresh process for each request to improve failure containment and keep lifecycle
behavior deterministic.

Request envelope:

```json
{"apiVersion":"carbongate.provider/v1","id":"UUID","operation":"health","deadlineMillis":2000,"payload":{}}
```

Successful response:

```json
{"apiVersion":"carbongate.provider/v1","id":"UUID","status":"ok","result":{"health":"pass"}}
```

The host clears the inherited environment except for the minimum process
launcher variables, limits each message to 1 MiB, retains at most 8 KiB of
standard error, enforces the declared deadline, and force-terminates timed-out
processes. Only the fixed `inspect`, `authorize`, `audit`, and `sandbox`
operations are accepted. Arbitrary JVM callbacks and third-party class loading
inside Core are not supported.
