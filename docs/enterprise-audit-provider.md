# Enterprise Audit Provider v1

The optional Enterprise Audit Provider implements the final `audit` stage of
the explicit Enterprise Guard Pipeline.

```bash
./scripts/build-audit.sh
./build/carbon-enterprise install build/enterprise-audit-provider-1.0.0.carbon
./build/carbon-enterprise enable enterprise-audit-provider 1.0.0
```

Windows uses `scripts\build-audit.ps1`. Audit data is stored below the active
component's `state` directory in daily UTC JSONL files.

Each record contains a sequence number, timestamp, previous hash, sanitized
event, and SHA-256 hash. Appends use an operating-system file lock and force the
record to stable storage. Health checks verify the complete daily forward hash
chain; a mismatch fails closed. Each daily file has a 100,000,000-byte safety
limit.

The Provider whitelists only `eventId`, `action`, `risk`, `decision`, and a
bounded list of component operation names. Unknown fields—including inspected
content and Sandbox output—are discarded. This enterprise component is
separate from the local Agent log, whose combined daily hard limit remains
10,000,000 bytes.
