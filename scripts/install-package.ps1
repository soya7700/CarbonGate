param(
    [string]$Prefix = "$env:LOCALAPPDATA\CarbonGate",
    [switch]$Setup,
    [string]$Hosts
)

$ErrorActionPreference = "Stop"
$Root = $PSScriptRoot
if (-not (Test-Path (Join-Path $Root "bin\carbongate.jar"))) {
    $Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
}
$SourceJar = Join-Path $Root "bin\carbongate.jar"

if (-not (Test-Path $SourceJar)) {
    throw "Packaged CarbonGate JAR is missing: $SourceJar"
}
if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    throw "java was not found. Install Java 21 or newer and add it to PATH."
}

& java -jar $SourceJar version | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw "CarbonGate requires Java 21 or newer, and the packaged JAR must be intact."
}

$LibDirectory = Join-Path $Prefix "lib\carbongate"
$BinDirectory = Join-Path $Prefix "bin"
New-Item -ItemType Directory -Force -Path $LibDirectory, $BinDirectory | Out-Null
Copy-Item -Force $SourceJar (Join-Path $LibDirectory "carbongate.jar")

$Launcher = Join-Path $BinDirectory "carbon.cmd"
$LauncherContent = @'
@echo off
java -jar "%~dp0..\lib\carbongate\carbongate.jar" %*
'@
Set-Content -Path $Launcher -Value $LauncherContent -Encoding ASCII

& $Launcher config init | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw "CarbonGate configuration initialization failed."
}

if ($Setup -or $Hosts) {
    $InstallCodexSkill = $false
    if ($Hosts) {
        $InstallCodexSkill = ($Hosts -split ",") -contains "codex"
    } else {
        $InstallCodexSkill = [bool](Get-Command codex -ErrorAction SilentlyContinue)
    }
    $SourceSkill = Join-Path $Root "skills\carbongate"
    if ($InstallCodexSkill -and (Test-Path (Join-Path $SourceSkill "SKILL.md"))) {
        $CodexState = if ($env:CODEX_HOME) { $env:CODEX_HOME } else { Join-Path $HOME ".codex" }
        $SkillParent = Join-Path $CodexState "skills"
        $SkillDestination = Join-Path $SkillParent "carbongate"
        if (-not (Test-Path $SkillDestination)) {
            New-Item -ItemType Directory -Force -Path $SkillParent | Out-Null
            Copy-Item -Recurse $SourceSkill $SkillDestination
        }
    }
    if ($Hosts) {
        & $Launcher setup --host $Hosts
    } else {
        & $Launcher setup
    }
    if ($LASTEXITCODE -ne 0) {
        throw "CarbonGate host setup failed. Run 'carbon doctor' for details."
    }
}

Write-Host "Installed CarbonGate to $Launcher"
Write-Host "Add $BinDirectory to your user PATH, then open a new terminal."
