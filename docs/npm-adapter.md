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
npx @carbongate/cli setup --version 0.3.0 --prefix "$HOME/.local"
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

Review the .tgz package output and publish only after the matching GitHub
Release is public:

~~~bash
cd adapters/npm/cli
npm publish --access public --provenance
~~~

The first scoped publication must explicitly use public access; npm documents
that scoped packages otherwise default to restricted visibility. See the
[official scoped public package guidance](https://docs.npmjs.com/creating-and-publishing-scoped-public-packages/)
and [npm publish reference](https://docs.npmjs.com/cli/publish/). Publication is
manual; no GitHub workflow publishes to npm.
