#!/usr/bin/env sh
set -eu

REPOSITORY=${CARBONGATE_REPOSITORY:-soya7700/CarbonGate}
VERSION=${CARBONGATE_VERSION:-}
PREFIX=
SETUP=false
HOSTS=

usage() {
  printf '%s\n' \
    'Usage: install-release.sh [--version VERSION] [--prefix PATH] [--setup] [--host HOSTS]' \
    '' \
    'Downloads one CarbonGate native release, verifies SHA-256, and runs its installer.'
}

while test "$#" -gt 0; do
  case "$1" in
    --version)
      test "$#" -ge 2 || { printf '%s\n' '--version requires a value' >&2; exit 2; }
      VERSION=$2
      shift 2
      ;;
    --prefix)
      test "$#" -ge 2 || { printf '%s\n' '--prefix requires a path' >&2; exit 2; }
      PREFIX=$2
      shift 2
      ;;
    --setup)
      SETUP=true
      shift
      ;;
    --host)
      test "$#" -ge 2 || { printf '%s\n' '--host requires a comma-separated host list' >&2; exit 2; }
      SETUP=true
      HOSTS=$2
      shift 2
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      printf 'Unknown option: %s\n' "$1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

command -v curl >/dev/null 2>&1 || {
  printf '%s\n' 'CarbonGate bootstrap requires curl.' >&2
  exit 1
}
command -v tar >/dev/null 2>&1 || {
  printf '%s\n' 'CarbonGate bootstrap requires tar.' >&2
  exit 1
}

case "$REPOSITORY" in
  *[!A-Za-z0-9._/-]*|/*|*/|*//*|*/*/*|'')
    printf 'Invalid CARBONGATE_REPOSITORY: %s\n' "$REPOSITORY" >&2
    exit 2
    ;;
esac

download() {
  source_url=$1
  destination=$2
  case "$source_url" in
    https://*) curl -fsSL --proto '=https' --tlsv1.2 -o "$destination" "$source_url" ;;
    file://*)
      test "${CARBONGATE_ALLOW_FILE_URLS:-0}" = 1 || {
        printf '%s\n' 'file:// sources require CARBONGATE_ALLOW_FILE_URLS=1.' >&2
        return 1
      }
      curl -fsSL -o "$destination" "$source_url"
      ;;
    *)
      printf 'Refusing non-HTTPS download URL: %s\n' "$source_url" >&2
      return 1
      ;;
  esac
}

if test -z "$VERSION"; then
  latest_url=$(curl -fsSL --proto '=https' --tlsv1.2 -o /dev/null \
    -w '%{url_effective}' "https://github.com/$REPOSITORY/releases/latest")
  VERSION=${latest_url%/}
  VERSION=${VERSION##*/}
  VERSION=${VERSION#v}
fi

printf '%s\n' "$VERSION" | grep -Eq '^[0-9]+\.[0-9]+\.[0-9]+([+-][A-Za-z0-9.-]+)?$' || {
  printf 'Invalid CarbonGate release version: %s\n' "$VERSION" >&2
  exit 2
}

case "$(uname -s):$(uname -m)" in
  Darwin:arm64) PLATFORM=darwin-arm64 ;;
  Linux:x86_64|Linux:amd64) PLATFORM=linux-x64 ;;
  Darwin:x86_64)
    printf '%s\n' 'macOS Intel currently uses the portable Java 21 archive; see the README.' >&2
    exit 1
    ;;
  *)
    printf 'No native CarbonGate release for %s/%s; see the portable Java 21 archive.\n' \
      "$(uname -s)" "$(uname -m)" >&2
    exit 1
    ;;
esac

WORK=$(mktemp -d "${TMPDIR:-/tmp}/carbongate-install.XXXXXX")
trap 'rm -rf "$WORK"' EXIT HUP INT TERM

MANIFEST_URL=${CARBONGATE_MANIFEST_URL:-"https://raw.githubusercontent.com/$REPOSITORY/main/distribution/release-assets.properties"}
MANIFEST="$WORK/release-assets.properties"
download "$MANIFEST_URL" "$MANIFEST"

property_value() {
  requested=$1
  while IFS='=' read -r key value || test -n "$key"; do
    case "$key" in ''|'#'*) continue ;; esac
    if test "$key" = "$requested"; then
      printf '%s\n' "$value"
      return 0
    fi
  done < "$MANIFEST"
  return 1
}

test "$(property_value schema.version)" = 1 || {
  printf '%s\n' 'Unsupported CarbonGate release manifest schema.' >&2
  exit 1
}

ASSET_PATTERN=$(property_value "native.$PLATFORM.asset") || {
  printf 'Release manifest does not support %s.\n' "$PLATFORM" >&2
  exit 1
}
case "$ASSET_PATTERN" in
  *'{version}'*) ;;
  *) printf '%s\n' 'Invalid release asset pattern.' >&2; exit 1 ;;
esac
ASSET_PREFIX=${ASSET_PATTERN%%\{version\}*}
ASSET_SUFFIX=${ASSET_PATTERN#*\{version\}}
ASSET=$ASSET_PREFIX$VERSION$ASSET_SUFFIX
case "$ASSET" in
  *[!A-Za-z0-9._-]*|.*|*..*) printf '%s\n' 'Unsafe release asset name.' >&2; exit 1 ;;
esac

RELEASE_BASE=${CARBONGATE_RELEASE_BASE_URL:-"https://github.com/$REPOSITORY/releases/download"}
RELEASE_BASE=${RELEASE_BASE%/}
ARCHIVE="$WORK/$ASSET"
CHECKSUM="$WORK/$ASSET.sha256"
download "$RELEASE_BASE/v$VERSION/$ASSET" "$ARCHIVE"
download "$RELEASE_BASE/v$VERSION/$ASSET.sha256" "$CHECKSUM"

IFS=' ' read -r EXPECTED CHECKSUM_NAME < "$CHECKSUM" || {
  printf '%s\n' 'Malformed CarbonGate checksum file.' >&2
  exit 1
}
CHECKSUM_NAME=${CHECKSUM_NAME#\*}
test "$CHECKSUM_NAME" = "$ASSET" || {
  printf '%s\n' 'Checksum filename does not match the downloaded release asset.' >&2
  exit 1
}
test "${#EXPECTED}" -eq 64 || { printf '%s\n' 'Malformed SHA-256 value.' >&2; exit 1; }
case "$EXPECTED" in *[!0-9A-Fa-f]*) printf '%s\n' 'Malformed SHA-256 value.' >&2; exit 1 ;; esac

if command -v shasum >/dev/null 2>&1; then
  ACTUAL=$(shasum -a 256 "$ARCHIVE" | cut -d ' ' -f 1)
elif command -v sha256sum >/dev/null 2>&1; then
  ACTUAL=$(sha256sum "$ARCHIVE" | cut -d ' ' -f 1)
else
  printf '%s\n' 'CarbonGate bootstrap requires shasum or sha256sum.' >&2
  exit 1
fi
test "$ACTUAL" = "$EXPECTED" || {
  printf '%s\n' 'CarbonGate release checksum verification failed.' >&2
  exit 1
}

if tar -tzf "$ARCHIVE" | grep -Eq '(^/|(^|/)\.\.(/|$))'; then
  printf '%s\n' 'Release archive contains an unsafe path.' >&2
  exit 1
fi
tar -xzf "$ARCHIVE" -C "$WORK"
PACKAGE=${ASSET%.tar.gz}
INSTALLER="$WORK/$PACKAGE/install.sh"
test -s "$INSTALLER" || { printf '%s\n' 'Release archive does not contain install.sh.' >&2; exit 1; }

set -- "$INSTALLER"
test -z "$PREFIX" || set -- "$@" --prefix "$PREFIX"
test "$SETUP" = false || set -- "$@" --setup
test -z "$HOSTS" || set -- "$@" --host "$HOSTS"
"$@"
