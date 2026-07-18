param(
    [Parameter(Mandatory = $true)]
    [string]$OutputDirectory
)

$ErrorActionPreference = "Stop"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$Version = (Get-Content -Raw (Join-Path $Root "VERSION")).Trim()
$GeneratedDirectory = Join-Path $Root "build\generated-sources\io\carbongate"
$Generated = Join-Path $GeneratedDirectory "BuildInfo.java"

if ($Version -notmatch '^[0-9]+\.[0-9]+\.[0-9]+([+-][A-Za-z0-9.-]+)?$') {
    throw "VERSION must contain a semantic version; found $Version."
}

New-Item -ItemType Directory -Force -Path $OutputDirectory, $GeneratedDirectory | Out-Null
$BuildInfo = @"
package io.carbongate;

public final class BuildInfo {
    public static final String VERSION = "$Version";
    private BuildInfo() {}

    public static boolean nativeImage() {
        return "runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"));
    }

    public static String runtimeLabel() {
        return nativeImage() ? "native" : "Java " + System.getProperty("java.specification.version", "unknown");
    }
}
"@
$Utf8WithoutBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($Generated, $BuildInfo, $Utf8WithoutBom)

$Sources = @(Get-ChildItem (Join-Path $Root "src\main\java") -Recurse -Filter "*.java" |
    Select-Object -ExpandProperty FullName)
$Sources += $Generated
if ($Sources.Count -le 1) {
    throw "No Java source files were found."
}

& javac --release 21 -encoding UTF-8 -d $OutputDirectory $Sources
if ($LASTEXITCODE -ne 0) {
    throw "CarbonGate compilation failed."
}
