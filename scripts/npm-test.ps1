$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$Package = Join-Path $Root "adapters\npm\cli"
$Version = (& node --version).Trim().TrimStart("v").Split(".")
if ([int]$Version[0] -lt 18 -or ([int]$Version[0] -eq 18 -and [int]$Version[1] -lt 17)) {
    throw "Node.js 18.17 or newer is required to verify the npm adapter."
}
$env:NPM_CONFIG_CACHE = Join-Path $Root "build\npm-test-cache"
& npm.cmd --prefix $Package test
if ($LASTEXITCODE -ne 0) { throw "CarbonGate npm adapter tests failed." }
Write-Host "CarbonGate npm adapter tests passed."
