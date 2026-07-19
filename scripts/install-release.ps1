param(
    [string]$Version,
    [string]$Prefix,
    [switch]$Setup,
    [string]$Hosts,
    [string]$Repository = "soya7700/CarbonGate"
)

$ErrorActionPreference = "Stop"
if (-not $Version) { $Version = $env:CARBONGATE_VERSION }
if ($Repository -notmatch '^[A-Za-z0-9._-]+/[A-Za-z0-9._-]+$') {
    throw "Invalid CarbonGate repository: $Repository"
}

function Assert-AllowedResource([string]$Resource) {
    $Uri = [Uri]$Resource
    if ($Uri.Scheme -eq "https") { return $Uri }
    if ($Uri.IsFile -and $env:CARBONGATE_ALLOW_FILE_URLS -eq "1") { return $Uri }
    throw "Refusing non-HTTPS resource: $Resource"
}

function Get-ResourceText([string]$Resource) {
    $Uri = Assert-AllowedResource $Resource
    if ($Uri.IsFile) { return [IO.File]::ReadAllText($Uri.LocalPath) }
    return (Invoke-WebRequest -UseBasicParsing -Uri $Uri.AbsoluteUri).Content
}

function Save-Resource([string]$Resource, [string]$Destination) {
    $Uri = Assert-AllowedResource $Resource
    if ($Uri.IsFile) { Copy-Item -Force $Uri.LocalPath $Destination; return }
    Invoke-WebRequest -UseBasicParsing -Uri $Uri.AbsoluteUri -OutFile $Destination
}

if (-not $Version) {
    $Release = Invoke-RestMethod -Headers @{ "User-Agent" = "CarbonGate-Installer" } `
        -Uri "https://api.github.com/repos/$Repository/releases/latest"
    $Version = [string]$Release.tag_name
    if ($Version.StartsWith("v")) { $Version = $Version.Substring(1) }
}
if ($Version -notmatch '^[0-9]+\.[0-9]+\.[0-9]+([+-][A-Za-z0-9.-]+)?$') {
    throw "Invalid CarbonGate release version: $Version"
}

$Architecture = [Runtime.InteropServices.RuntimeInformation]::OSArchitecture.ToString().ToLowerInvariant()
if ($Architecture -notin @("x64", "x86_64")) {
    throw "No native CarbonGate Windows release for architecture $Architecture; use the portable Java 21 archive."
}
$Platform = "windows-x64"

$Work = Join-Path ([IO.Path]::GetTempPath()) ("carbongate-install-" + [Guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Path $Work | Out-Null
try {
    $ManifestUrl = if ($env:CARBONGATE_MANIFEST_URL) { $env:CARBONGATE_MANIFEST_URL } else {
        "https://raw.githubusercontent.com/$Repository/main/distribution/release-assets.properties"
    }
    $Manifest = @{}
    foreach ($Line in (Get-ResourceText $ManifestUrl) -split "`r?`n") {
        $Trimmed = $Line.Trim()
        if (-not $Trimmed -or $Trimmed.StartsWith("#")) { continue }
        $Parts = $Trimmed.Split(@("="), 2, [StringSplitOptions]::None)
        if ($Parts.Count -ne 2 -or $Manifest.ContainsKey($Parts[0])) {
            throw "Malformed CarbonGate release manifest."
        }
        $Manifest[$Parts[0]] = $Parts[1]
    }
    if ($Manifest["schema.version"] -ne "1") { throw "Unsupported CarbonGate release manifest schema." }
    $Pattern = $Manifest["native.$Platform.asset"]
    if (-not $Pattern -or -not $Pattern.Contains("{version}")) { throw "Release manifest does not support $Platform." }
    $Asset = $Pattern.Replace("{version}", $Version)
    if ($Asset -notmatch '^[A-Za-z0-9][A-Za-z0-9._-]+$' -or $Asset.Contains("..")) {
        throw "Unsafe release asset name."
    }

    $ReleaseBase = if ($env:CARBONGATE_RELEASE_BASE_URL) { $env:CARBONGATE_RELEASE_BASE_URL.TrimEnd("/") } else {
        "https://github.com/$Repository/releases/download"
    }
    $Archive = Join-Path $Work $Asset
    $Checksum = "$Archive.sha256"
    Save-Resource "$ReleaseBase/v$Version/$Asset" $Archive
    Save-Resource "$ReleaseBase/v$Version/$Asset.sha256" $Checksum

    $ChecksumLine = ([IO.File]::ReadAllText($Checksum)).Trim()
    if ($ChecksumLine -notmatch '^([A-Fa-f0-9]{64})\s+\*?([^\r\n]+)$') { throw "Malformed CarbonGate checksum file." }
    if ($Matches[2] -ne $Asset) { throw "Checksum filename does not match the downloaded release asset." }
    $Actual = (Get-FileHash -Algorithm SHA256 -Path $Archive).Hash
    if ($Actual -ne $Matches[1]) { throw "CarbonGate release checksum verification failed." }

    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $Zip = [IO.Compression.ZipFile]::OpenRead($Archive)
    try {
        $WorkRoot = [IO.Path]::GetFullPath($Work + [IO.Path]::DirectorySeparatorChar)
        foreach ($Entry in $Zip.Entries) {
            $Destination = [IO.Path]::GetFullPath((Join-Path $Work $Entry.FullName))
            if (-not $Destination.StartsWith($WorkRoot, [StringComparison]::OrdinalIgnoreCase)) {
                throw "Release archive contains an unsafe path."
            }
        }
    } finally {
        $Zip.Dispose()
    }
    Expand-Archive -Path $Archive -DestinationPath $Work -Force
    $Package = $Asset.Substring(0, $Asset.Length - 4)
    $Installer = Join-Path $Work "$Package\install.ps1"
    if (-not (Test-Path $Installer -PathType Leaf)) { throw "Release archive does not contain install.ps1." }

    $InstallerParameters = @{}
    if ($Prefix) { $InstallerParameters["Prefix"] = $Prefix }
    if ($Setup) { $InstallerParameters["Setup"] = $true }
    if ($Hosts) { $InstallerParameters["Hosts"] = $Hosts }
    & $Installer @InstallerParameters
    if ($LASTEXITCODE -ne 0) { throw "CarbonGate package installer failed." }
} finally {
    Remove-Item -Recurse -Force $Work -ErrorAction SilentlyContinue
}
