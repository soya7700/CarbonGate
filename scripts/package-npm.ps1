param([switch]$SkipTests)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$Package = Join-Path $Root "adapters\npm\cli"
$ProjectVersion = [IO.File]::ReadAllText((Join-Path $Root "VERSION")).Trim()
$PackageVersion = (& node -e "process.stdout.write(require(process.argv[1]).version)" (Join-Path $Package "package.json")).Trim()
if ($ProjectVersion -ne $PackageVersion) {
    throw "npm package version $PackageVersion does not match VERSION $ProjectVersion."
}
if (-not $SkipTests) {
    & (Join-Path $Root "scripts\npm-test.ps1")
    if ($LASTEXITCODE -ne 0) { throw "CarbonGate npm adapter tests failed." }
}

$Destination = Join-Path $Root "build\npm"
New-Item -ItemType Directory -Force -Path $Destination | Out-Null
$env:NPM_CONFIG_CACHE = Join-Path $Root "build\npm-package-cache"
& npm.cmd pack --ignore-scripts --pack-destination $Destination $Package
if ($LASTEXITCODE -ne 0) { throw "CarbonGate npm package build failed." }
Write-Host "Packaged $(Join-Path $Destination "carbongate-cli-$ProjectVersion.tgz")"
