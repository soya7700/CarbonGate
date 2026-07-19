# npm adapter

@carbongate/cli is a small distribution adapter, not another policy engine.
It uses only Node.js built-in modules and delegates installation to a verified
CarbonGate native Release. It does not add Node.js, npm, network clients, or
any package-manager behavior to the Java 21 Core.

## User contract

After a maintainer has published a package whose version matches a public
CarbonGate GitHub Release, users can run:

~~~bash
npx @carbongate/cli install
npx @carbongate/cli setup
npx @carbongate/cli setup --host codex,claude,openclaw
npx @carbongate/cli setup --version 0.3.1 --prefix "$HOME/.local"
~~~

install never changes an Agent host configuration. setup explicitly installs
and configures detected hosts, or the hosts selected by --host. The adapter
supports macOS Apple Silicon, Linux x64, and Windows x64. It reads
the data-only release contract bundled in the npm package, resolves the latest
GitHub Release only when no version is specified, downloads the matching native
archive and its .sha256 file, verifies the digest, validates archive paths,
then runs the package installer.

Installation of the npm package itself performs no network request and declares
no preinstall, install, postinstall, prepare, or prepack lifecycle script.
install and setup are the only commands that may download a release.

HTTPS is mandatory by default. A controlled offline mirror can set
CARBONGATE_RELEASE_BASE_URL; file:// is additionally gated by the explicit
CARBONGATE_ALLOW_FILE_URLS=1 opt-in. CARBONGATE_VERSION provides a default
version and CARBONGATE_LATEST_URL can point to a controlled latest-release
response using the same protocol rules.

When the default unauthenticated GitHub API lookup is rate-limited, the adapter
does not ask for a token. It resolves the public `https://github.com/OWNER/REPO/releases/latest`
redirect instead, validates that the redirect remains within the manifest's
repository and has a semantic-version tag, then verifies the selected release
archive with its published SHA-256 file. A fixed `--version` always bypasses
latest-release resolution entirely.

## Maintainer contract

The package source is adapters/npm/cli. Its version must match the repository
root VERSION, and its release asset contract must exactly match
distribution/release-assets.properties. The adapter includes the root Apache
2.0 LICENSE, NOTICE, and third-party notice file.

Before publication, run:

~~~bash
./scripts/npm-test.sh
./scripts/package-npm.sh
~~~

On Windows:

~~~powershell
.\scripts\npm-test.ps1
.\scripts\package-npm.ps1
~~~

Review the .tgz package output. The first public package was published with
npm 2FA. Later publications use npm Trusted Publishing: a maintainer manually
dispatches `.github/workflows/npm-publish.yml` from the matching public
`vVERSION` GitHub Release tag. The workflow verifies the versions, checks that
the version is not already public, and publishes with an OIDC provenance
attestation. It never runs automatically on a push, tag, or GitHub Release.

The npm package Settings page must bind its trusted publisher to GitHub user
`soya7700`, repository `CarbonGate`, and workflow filename `npm-publish.yml`.
The workflow's `npm-publish` GitHub Environment is the manual approval point.

For emergency bootstrap only, a maintainer with npm 2FA can publish directly
after the matching GitHub Release is public:

~~~bash
cd adapters/npm/cli
npm publish --access public --provenance
~~~

The first scoped publication must explicitly use public access; npm documents
that scoped packages otherwise default to restricted visibility. See the
[official scoped public package guidance](https://docs.npmjs.com/creating-and-publishing-scoped-public-packages/),
[npm publish reference](https://docs.npmjs.com/cli/publish/), and
[trusted publishing guidance](https://docs.npmjs.com/trusted-publishers/).
