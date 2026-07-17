param()

$ErrorActionPreference = "Stop"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$Source = Join-Path $Root "components\sandboxes\container-sandbox"
$Classes = Join-Path $Root "build\container-sandbox-classes"
$Stage = Join-Path $Root "build\component-source\container-sandbox"
$SandboxJar = Join-Path $Root "build\container-sandbox.jar"
$Output = Join-Path $Root "build\container-sandbox-1.0.0.carbon"

& (Join-Path $Root "scripts\build-enterprise.ps1")
if ($LASTEXITCODE -ne 0) { throw "Enterprise Component Host build failed." }
if (Test-Path $Classes) { Remove-Item -Recurse -Force $Classes }
if (Test-Path $Stage) { Remove-Item -Recurse -Force $Stage }
New-Item -ItemType Directory -Force -Path $Classes | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $Stage "payload") | Out-Null
$Sources = Get-ChildItem (Join-Path $Source "src\main\java") -Recurse -Filter "*.java" |
    Select-Object -ExpandProperty FullName
& javac --release 21 -Xlint:all -Werror -encoding UTF-8 -cp (Join-Path $Root "build\carbongate.jar") -d $Classes $Sources
if ($LASTEXITCODE -ne 0) { throw "Container Sandbox Provider compilation failed." }
& jar --create --file $SandboxJar --main-class io.carbongate.sandbox.container.ContainerSandboxProvider `
    -C $Classes . -C (Join-Path $Root "build\classes") "io/carbongate/json"
if ($LASTEXITCODE -ne 0) { throw "Container Sandbox Provider JAR creation failed." }
Copy-Item (Join-Path $Source "manifest.json") $Stage
Copy-Item (Join-Path $Source "NOTICE") $Stage
Copy-Item (Join-Path $Root "LICENSE") $Stage
Copy-Item $SandboxJar (Join-Path $Stage "payload\sandbox.jar")
& (Join-Path $Root "build\carbon-enterprise.cmd") package $Stage $Output
if ($LASTEXITCODE -ne 0) { throw "Container Sandbox Provider package build failed." }
Write-Host "Built Container Sandbox Provider $Output"
