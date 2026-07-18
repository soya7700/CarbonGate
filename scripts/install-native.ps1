param(
    [string]$Prefix = "$env:LOCALAPPDATA\CarbonGate",
    [switch]$Setup,
    [string]$Hosts
)

$ErrorActionPreference = "Stop"
$Source = Join-Path $PSScriptRoot "bin\carbon.exe"
if (-not (Test-Path $Source)) {
    throw "Packaged CarbonGate executable is missing: $Source"
}

$BinDirectory = Join-Path $Prefix "bin"
New-Item -ItemType Directory -Force -Path $BinDirectory | Out-Null
$Launcher = Join-Path $BinDirectory "carbon.exe"
Copy-Item -Force $Source $Launcher

& $Launcher version | Out-Null
if ($LASTEXITCODE -ne 0) { throw "CarbonGate executable validation failed." }
& $Launcher config init | Out-Null
if ($LASTEXITCODE -ne 0) { throw "CarbonGate configuration initialization failed." }

if ($Setup -or $Hosts) {
    $InstallCodexSkill = if ($Hosts) { ($Hosts -split ",") -contains "codex" } else {
        [bool](Get-Command codex -ErrorAction SilentlyContinue)
    }
    $SourceSkill = Join-Path $PSScriptRoot "skills\carbongate"
    if ($InstallCodexSkill -and (Test-Path (Join-Path $SourceSkill "SKILL.md"))) {
        $CodexState = if ($env:CODEX_HOME) { $env:CODEX_HOME } else { Join-Path $HOME ".codex" }
        $SkillParent = Join-Path $CodexState "skills"
        $SkillDestination = Join-Path $SkillParent "carbongate"
        if (-not (Test-Path $SkillDestination)) {
            New-Item -ItemType Directory -Force -Path $SkillParent | Out-Null
            Copy-Item -Recurse $SourceSkill $SkillDestination
        }
    }
    if ($Hosts) { & $Launcher setup --host $Hosts } else { & $Launcher setup }
    if ($LASTEXITCODE -ne 0) { throw "CarbonGate host setup failed. Run 'carbon doctor' for details." }
}

Write-Host "Installed CarbonGate to $Launcher"
Write-Host "Add $BinDirectory to your user PATH, then open a new terminal."
