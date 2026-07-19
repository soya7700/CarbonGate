$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$Version = [IO.File]::ReadAllText((Join-Path $Root "VERSION")).Trim()
$TestRoot = Join-Path ([IO.Path]::GetTempPath()) ("carbongate-distribution-test-" + [Guid]::NewGuid().ToString("N"))

try {
    $Asset = "carbongate-$Version-windows-x64.zip"
    $Package = $Asset.Substring(0, $Asset.Length - 4)
    $Stage = Join-Path $TestRoot "stage\$Package"
    $Release = Join-Path $TestRoot "releases\download\v$Version"
    $Prefix = Join-Path $TestRoot "prefix"
    New-Item -ItemType Directory -Force -Path $Stage, $Release | Out-Null

    @'
param([string]$Prefix, [switch]$Setup, [string]$Hosts)
$ErrorActionPreference = "Stop"
New-Item -ItemType Directory -Force -Path (Join-Path $Prefix "bin") | Out-Null
Set-Content -Encoding UTF8 -Path (Join-Path $Prefix "bin\carbon.exe") -Value "fixture"
$global:LASTEXITCODE = 0
'@ | Set-Content -Encoding UTF8 -Path (Join-Path $Stage "install.ps1")

    Compress-Archive -Path (Join-Path (Split-Path -Parent $Stage) $Package) -DestinationPath (Join-Path $Release $Asset)
    $Hash = (Get-FileHash -Algorithm SHA256 -Path (Join-Path $Release $Asset)).Hash.ToLowerInvariant()
    Set-Content -Encoding ASCII -Path (Join-Path $Release "$Asset.sha256") -Value "$Hash  $Asset"

    $env:CARBONGATE_ALLOW_FILE_URLS = "1"
    $env:CARBONGATE_MANIFEST_URL = ([Uri](Join-Path $Root "distribution\release-assets.properties")).AbsoluteUri
    $env:CARBONGATE_RELEASE_BASE_URL = ([Uri](Join-Path $TestRoot "releases\download")).AbsoluteUri.TrimEnd("/")
    & (Join-Path $Root "scripts\install-release.ps1") -Version $Version -Prefix $Prefix -Setup
    if (-not (Test-Path (Join-Path $Prefix "bin\carbon.exe"))) { throw "Windows bootstrap did not invoke the packaged installer." }

    Set-Content -Encoding ASCII -Path (Join-Path $Release "$Asset.sha256") -Value "$('0' * 64)  $Asset"
    $Rejected = $false
    try {
        & (Join-Path $Root "scripts\install-release.ps1") -Version $Version -Prefix (Join-Path $TestRoot "rejected-prefix")
    } catch {
        $Rejected = $_.Exception.Message -match "checksum verification failed"
    }
    if (-not $Rejected) { throw "Windows bootstrap accepted a release with the wrong checksum." }

    Write-Host "Windows release contract and verified bootstrap installation passed."
} finally {
    Remove-Item Env:CARBONGATE_ALLOW_FILE_URLS -ErrorAction SilentlyContinue
    Remove-Item Env:CARBONGATE_MANIFEST_URL -ErrorAction SilentlyContinue
    Remove-Item Env:CARBONGATE_RELEASE_BASE_URL -ErrorAction SilentlyContinue
    Remove-Item -Recurse -Force $TestRoot -ErrorAction SilentlyContinue
}
