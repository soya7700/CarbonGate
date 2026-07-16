# Dependency and license policy

Every proposed dependency must be reviewed before merge. The review records:

- package name, exact version, source URL, and checksum or lockfile entry
- declared license and a link to its canonical license text
- all transitive runtime dependencies and their licenses
- whether source, notices, or modifications are redistributed
- known security advisories and the update owner

Permissive licenses such as Apache-2.0, MIT, BSD-2-Clause, and BSD-3-Clause are
generally acceptable when their notice conditions are satisfied. Weak copyleft
and unusual licenses require an explicit compatibility review. GPL, AGPL, SSPL,
BUSL, Commons Clause, non-commercial, source-available, and unknown-license
components must not enter the core or distributed artifacts without a documented
legal and architectural decision.

Dependencies must be version-pinned. Copying snippets from external projects is
treated as a dependency and requires provenance plus attribution. Build plugins,
GitHub Actions, container images, fonts, icons, generated code, and test-only
libraries are in scope; "development only" is not a license exemption.

Release review must verify `LICENSE`, `NOTICE`, this policy, and
`THIRD_PARTY_NOTICES.md`, and must inspect the built artifact for undeclared
bundled code.
