param()

$ErrorActionPreference = "Stop"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$Build = Join-Path $Root "build"
$Core = Join-Path $Build "carbongate.jar"
$Classes = Join-Path $Build "enterprise-classes"
$HostJar = Join-Path $Build "carbongate-enterprise-host.jar"

if (-not (Test-Path $Core)) {
    throw "Build carbongate.jar before the optional Enterprise Component Host."
}
if (Test-Path $Classes) {
    Remove-Item -Recurse -Force $Classes
}
New-Item -ItemType Directory -Force -Path $Classes | Out-Null
$Sources = Get-ChildItem (Join-Path $Root "enterprise\src\main\java") -Recurse -Filter "*.java" |
    Select-Object -ExpandProperty FullName
& javac --release 21 -Xlint:all -Werror -encoding UTF-8 -cp $Core -d $Classes $Sources
if ($LASTEXITCODE -ne 0) { throw "Enterprise Component Host compilation failed." }
& jar --create --file $HostJar --main-class io.carbongate.enterprise.cli.EnterpriseCli -C $Classes .
if ($LASTEXITCODE -ne 0) { throw "Enterprise Component Host JAR creation failed." }

$Launcher = Join-Path $Build "carbon-enterprise.cmd"
$LauncherContent = @'
@echo off
java -cp "%~dp0carbongate.jar;%~dp0carbongate-enterprise-host.jar" io.carbongate.enterprise.cli.EnterpriseCli %*
'@
Set-Content -Path $Launcher -Value $LauncherContent -Encoding ASCII
Write-Host "Built optional Enterprise Component Host $HostJar"
