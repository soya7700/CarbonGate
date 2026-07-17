param()

$ErrorActionPreference = "Stop"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$Source = Join-Path $Root "components\providers\enterprise-audit-provider"
$Classes = Join-Path $Root "build\enterprise-audit-provider-classes"
$Stage = Join-Path $Root "build\component-source\enterprise-audit-provider"
$ProviderJar = Join-Path $Root "build\enterprise-audit-provider.jar"
$Output = Join-Path $Root "build\enterprise-audit-provider-1.0.0.carbon"

& (Join-Path $Root "scripts\build-enterprise.ps1")
if ($LASTEXITCODE -ne 0) { throw "Enterprise Component Host build failed." }
if (Test-Path $Classes) { Remove-Item -Recurse -Force $Classes }
if (Test-Path $Stage) { Remove-Item -Recurse -Force $Stage }
New-Item -ItemType Directory -Force -Path $Classes | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $Stage "payload") | Out-Null
$Sources = Get-ChildItem (Join-Path $Source "src\main\java") -Recurse -Filter "*.java" | Select-Object -ExpandProperty FullName
& javac --release 21 -Xlint:all -Werror -encoding UTF-8 -cp (Join-Path $Root "build\carbongate.jar") -d $Classes $Sources
if ($LASTEXITCODE -ne 0) { throw "Enterprise Audit Provider compilation failed." }
& jar --create --file $ProviderJar --main-class io.carbongate.provider.audit.EnterpriseAuditProvider `
    -C $Classes . -C (Join-Path $Root "build\classes") "io/carbongate/json"
if ($LASTEXITCODE -ne 0) { throw "Enterprise Audit Provider JAR creation failed." }
Copy-Item (Join-Path $Source "manifest.json") $Stage
Copy-Item (Join-Path $Source "NOTICE") $Stage
Copy-Item (Join-Path $Root "LICENSE") $Stage
Copy-Item $ProviderJar (Join-Path $Stage "payload\provider.jar")
& (Join-Path $Root "build\carbon-enterprise.cmd") package $Stage $Output
if ($LASTEXITCODE -ne 0) { throw "Enterprise Audit Provider package build failed." }
Write-Host "Built Enterprise Audit Provider $Output"
