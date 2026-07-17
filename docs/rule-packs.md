# Rule Packs v1

CarbonGate Packs are optional, declarative data. They cannot declare an
entrypoint or execute code. The first-party `sensitive-data-baseline` Pack
contains personal and enterprise sensitive-data categories; it is not included
in the lightweight Core JAR.

## Build and enable the baseline

```bash
./scripts/build-pack.sh
./build/carbon-enterprise install build/sensitive-data-baseline-1.0.0.carbon
./build/carbon-enterprise enable sensitive-data-baseline 1.0.0
```

## Write a custom rule

Each Pack contains `payload/pack.json`:

```json
{
  "apiVersion": "carbongate.pack/v1",
  "rules": [
    {
      "id": "company.internal-label",
      "audience": "enterprise",
      "category": "enterprise.internal",
      "severity": "high",
      "match": {
        "type": "keywords",
        "terms": ["internal-only", "project-carbon"]
      }
    }
  ]
}
```

The stable fields are:

| Field | Allowed values |
|---|---|
| `id` | lower-case stable identifier |
| `audience` | `personal`, `enterprise`, `both` |
| `category` | lower-case dotted identifier |
| `severity` | `low`, `medium`, `high`, `critical` |
| `match.type` | `keywords`, `email`, `phone_cn`, `id_cn`, `bank_card`, `api_secret` |
| `match.terms` | 1–64 unique terms, only for `keywords` |

Arbitrary regular expressions and executable callbacks are deliberately not
accepted. This keeps custom rules reviewable and avoids regular-expression
denial-of-service in the host. A Pack can contain at most 256 rules and its
document can be at most 1 MiB.

## Package a Pack

Create a component source directory with `manifest.json`, `LICENSE`, `NOTICE`,
and `payload/pack.json`, then run:

```bash
./build/carbon-enterprise package /path/to/source /path/to/rules.carbon
```

The package command validates the manifest and rule schema, calculates every
payload checksum, and writes a deterministic ZIP-compatible `.carbon` archive.
