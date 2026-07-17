param()

$ErrorActionPreference = "Stop"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$Source = Join-Path $Root "components\packs\sensitive-data-baseline"
$Stage = Join-Path $Root "build\component-source\sensitive-data-baseline"
$Output = Join-Path $Root "build\sensitive-data-baseline-1.0.0.carbon"

& (Join-Path $Root "scripts\build-enterprise.ps1")
if ($LASTEXITCODE -ne 0) { throw "Enterprise Component Host build failed." }
if (Test-Path $Stage) { Remove-Item -Recurse -Force $Stage }
New-Item -ItemType Directory -Force -Path $Stage | Out-Null
Copy-Item (Join-Path $Source "*") $Stage -Recurse
Copy-Item (Join-Path $Root "LICENSE") (Join-Path $Stage "LICENSE")
& (Join-Path $Root "build\carbon-enterprise.cmd") package $Stage $Output
if ($LASTEXITCODE -ne 0) { throw "Sensitive Data Baseline Pack build failed." }
Write-Host "Built data-only Pack $Output"
