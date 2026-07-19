# Contributing to CarbonGate

Thank you for helping improve CarbonGate. The project accepts changes through
GitHub pull requests; the `main` branch is protected and is not a direct-write
integration branch.

## Development requirements

- JDK 21 or newer for source builds; all committed Java code targets Java 21.
- A POSIX shell for the complete local verification script.
- GraalVM Community Native Image only when changing native distribution code.

Run the complete JVM verification before opening a pull request:

```sh
./scripts/verify.sh
```

When Native Image is available, also run:

```sh
./scripts/native-test.sh
```

## Pull requests

1. Fork the repository and create a focused branch.
2. Keep Core lightweight and put optional enterprise behavior in components.
3. Add or update tests for every behavior change.
4. Update both `README.md` and `README-CN.md` when public usage changes.
5. Declare every external dependency and license in `THIRD_PARTY_NOTICES.md`.
6. Open a pull request and resolve all required checks and review conversations.

Do not include credentials, personal data, customer data, generated binaries,
or copied code without documented provenance and a compatible license.

## Community standards and support

Please follow the [Code of Conduct](CODE_OF_CONDUCT.md) in every project space.
For usage questions, start with [SUPPORT.md](SUPPORT.md); suspected security
issues must be reported through [SECURITY.md](SECURITY.md), not public issues.

## Releases

Maintainers use semantic versions from `VERSION`. A release candidate must be
merged into protected `main`, pass all JVM and native checks, and be published
through the manually dispatched release workflow.
