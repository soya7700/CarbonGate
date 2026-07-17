param(
    [string]$Prefix = "$env:LOCALAPPDATA\CarbonGate",
    [switch]$Setup,
    [string]$Hosts
)

$ErrorActionPreference = "Stop"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$Build = Join-Path $Root "build"
$Classes = Join-Path $Build "classes"
$Jar = Join-Path $Build "carbongate.jar"

if (-not (Get-Command javac -ErrorAction SilentlyContinue)) {
    throw "javac was not found. Install JDK 21 or newer and add it to PATH."
}
if (-not (Get-Command jar -ErrorAction SilentlyContinue)) {
    throw "jar was not found. Install JDK 21 or newer and add it to PATH."
}

if (Test-Path $Build) {
    Remove-Item -Recurse -Force $Build
}
New-Item -ItemType Directory -Force -Path $Classes | Out-Null

$Sources = Get-ChildItem (Join-Path $Root "src\main\java") -Recurse -Filter "*.java" |
    Select-Object -ExpandProperty FullName
if ($Sources.Count -eq 0) {
    throw "No Java source files were found."
}

& javac --release 21 -encoding UTF-8 -d $Classes $Sources
if ($LASTEXITCODE -ne 0) {
    throw "CarbonGate compilation failed."
}

& jar --create --file $Jar --main-class io.carbongate.cli.CarbonCli -C $Classes .
if ($LASTEXITCODE -ne 0) {
    throw "CarbonGate JAR creation failed."
}

$LibDirectory = Join-Path $Prefix "lib\carbongate"
$BinDirectory = Join-Path $Prefix "bin"
New-Item -ItemType Directory -Force -Path $LibDirectory, $BinDirectory | Out-Null
Copy-Item -Force $Jar (Join-Path $LibDirectory "carbongate.jar")

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
