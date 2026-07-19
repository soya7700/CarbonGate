# @carbongate/cli

`@carbongate/cli` is the small npm distribution adapter for CarbonGate. It has
no runtime dependencies and does nothing during npm installation. Only the
explicit `install` or `setup` command downloads a CarbonGate native Release, verifies its
published SHA-256 file, and invokes the installer already inside that Release.

## Install CarbonGate

```bash
npx @carbongate/cli install
```

`install` does not change any Agent host configuration. To install and then
configure detected hosts, use:

```bash
npx @carbongate/cli setup
```

Select specific local Agent hosts:

```bash
npx @carbongate/cli setup --host codex,claude,openclaw
```

Pin the CarbonGate Release or installation destination:

```bash
npx @carbongate/cli install --version 0.3.0 --prefix "$HOME/.local"
```

The adapter supports macOS Apple Silicon, Linux x64, and Windows x64. Other
platforms should use CarbonGate's portable Java 21 archive.

## Safety contract

- No `preinstall`, `install`, `postinstall`, `prepare`, or `prepack` lifecycle
  script is present.
- Only HTTPS is accepted by default.
- Every downloaded native archive is checked against its adjacent `.sha256`
  release checksum before extraction.
- Archive paths are checked before extraction.
- Offline testing or a controlled mirror may use `file://` only when
  `CARBONGATE_ALLOW_FILE_URLS=1` is explicitly set. Use
  `CARBONGATE_RELEASE_BASE_URL` to select that mirror.

`CARBONGATE_VERSION` supplies a default release version. If no version is set,
the adapter asks the official GitHub Releases API for the latest version.

## Maintainer publication

This package is published manually after the matching CarbonGate GitHub Release
is public. Review the package contents first:

```bash
./scripts/npm-test.sh
./scripts/package-npm.sh
```

Then, from this directory, a maintainer may run:

```bash
npm publish --access public --provenance
```

The package is Apache-2.0 licensed. See the repository root for the full
CarbonGate security model and Java 21 enterprise integration.
