import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { createHash } from "node:crypto";
import { promises as fs } from "node:fs";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import { fileURLToPath, pathToFileURL } from "node:url";
import { parseManifest, parseSetupOptions, supportedPlatform } from "../lib/adapter.js";

const packageDirectory = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const repositoryRoot = path.resolve(packageDirectory, "../../..");
const adapterBin = path.join(packageDirectory, "bin", "carbongate.js");
const projectVersion = (await fs.readFile(path.join(repositoryRoot, "VERSION"), "utf8")).trim();

test("package stays dependency-free and has no install lifecycle hook", async () => {
  const packageJson = JSON.parse(await fs.readFile(path.join(packageDirectory, "package.json"), "utf8"));
  assert.equal(packageJson.name, "@carbongate/cli");
  assert.equal(packageJson.version, projectVersion);
  assert.deepEqual(packageJson.dependencies ?? {}, {});
  assert.deepEqual(packageJson.optionalDependencies ?? {}, {});
  assert.deepEqual(packageJson.devDependencies ?? {}, {});
  for (const lifecycle of ["preinstall", "install", "postinstall", "prepare", "prepack"]) {
    assert.equal(packageJson.scripts?.[lifecycle], undefined, `${lifecycle} must not be defined`);
  }
  assert.equal(await fs.readFile(path.join(packageDirectory, "LICENSE"), "utf8"),
    await fs.readFile(path.join(repositoryRoot, "LICENSE"), "utf8"));
  assert.equal(await fs.readFile(path.join(packageDirectory, "NOTICE"), "utf8"),
    await fs.readFile(path.join(repositoryRoot, "NOTICE"), "utf8"));
  assert.equal(await fs.readFile(path.join(packageDirectory, "THIRD_PARTY_NOTICES.md"), "utf8"),
    await fs.readFile(path.join(repositoryRoot, "THIRD_PARTY_NOTICES.md"), "utf8"));
  assert.equal(await fs.readFile(path.join(packageDirectory, "distribution", "release-assets.properties"), "utf8"),
    await fs.readFile(path.join(repositoryRoot, "distribution", "release-assets.properties"), "utf8"));
});

test("manifest and platform validation reject unsupported inputs", () => {
  assert.equal(supportedPlatform("linux", "x64"), "linux-x64");
  assert.throws(() => supportedPlatform("darwin", "x64"), /portable Java 21 archive/);
  assert.throws(() => parseManifest("schema.version=1\nrepository=owner/repo\nrepository=duplicate\n"), /malformed/);
  assert.throws(() => parseSetupOptions(["--version", "not-a-version"], {}), /invalid/);
});

test("setup verifies a fixture release and delegates to its packaged installer", async () => {
  await withFixture(async ({ environment, prefix }) => {
    const result = runAdapter(["setup", "--version", projectVersion, "--prefix", prefix, "--host", "codex"], environment);
    assert.equal(result.status, 0, result.stderr);
    assert.equal((await fs.readFile(path.join(prefix, "installed.txt"), "utf8")).trim(), "host=codex");
    assert.match(result.stdout, /Installed verified CarbonGate/);
  });
});

test("setup rejects a fixture release whose checksum does not match", async () => {
  await withFixture(async ({ environment, checksumPath, prefix }) => {
    await fs.writeFile(checksumPath, `${"0".repeat(64)}  ${path.basename(checksumPath, ".sha256")}\n`, "utf8");
    const result = runAdapter(["setup", "--version", projectVersion, "--prefix", prefix], environment);
    assert.notEqual(result.status, 0);
    assert.match(result.stderr, /checksum verification failed/);
  });
});

test("install verifies a fixture release without configuring Agent hosts", async () => {
  await withFixture(async ({ environment, prefix }) => {
    const result = runAdapter(["install", "--version", projectVersion, "--prefix", prefix], environment);
    assert.equal(result.status, 0, result.stderr);
    assert.equal((await fs.readFile(path.join(prefix, "installed.txt"), "utf8")).trim(), "install");
  });
});

test("npm package dry run contains only the declared adapter surface", async () => {
  const cache = await fs.mkdtemp(path.join(os.tmpdir(), "carbongate-npm-cache-"));
  try {
    const result = runCommand(npmCommand(), ["pack", "--dry-run", "--json", "--ignore-scripts"], packageDirectory,
      { ...process.env, NPM_CONFIG_CACHE: cache }, { shell: process.platform === "win32" });
    assert.equal(result.status, 0, result.stderr);
    const packagePlan = JSON.parse(result.stdout);
    const files = packagePlan[0].files.map((entry) => entry.path);
    for (const required of ["bin/carbongate.js", "lib/adapter.js", "distribution/release-assets.properties", "LICENSE", "NOTICE", "README.md"]) {
      assert.ok(files.includes(required), `missing ${required}`);
    }
    assert.equal(files.some((file) => file.startsWith("test/")), false);
  } finally {
    await fs.rm(cache, { recursive: true, force: true });
  }
});

async function withFixture(callback) {
  const root = await fs.mkdtemp(path.join(os.tmpdir(), "carbongate-npm-test-"));
  try {
    const platform = supportedPlatform();
    const extension = platform === "windows-x64" ? ".zip" : ".tar.gz";
    const asset = `carbongate-${projectVersion}-${platform}${extension}`;
    const packageName = asset.replace(/\.(tar\.gz|zip)$/, "");
    const stage = path.join(root, "stage", packageName);
    const releaseDirectory = path.join(root, "releases", "download", `v${projectVersion}`);
    const archive = path.join(releaseDirectory, asset);
    const prefix = path.join(root, "prefix");
    await fs.mkdir(stage, { recursive: true });
    await fs.mkdir(releaseDirectory, { recursive: true });
    await writeFixtureInstaller(stage, platform);
    await createFixtureArchive(stage, archive, platform);
    const digest = createHash("sha256").update(await fs.readFile(archive)).digest("hex");
    const checksumPath = `${archive}.sha256`;
    await fs.writeFile(checksumPath, `${digest}  ${asset}\n`, "utf8");
    await callback({
      prefix,
      checksumPath,
      environment: {
        ...process.env,
        CARBONGATE_ALLOW_FILE_URLS: "1",
        CARBONGATE_RELEASE_BASE_URL: pathToFileURL(path.join(root, "releases", "download")).href
      }
    });
  } finally {
    await fs.rm(root, { recursive: true, force: true });
  }
}

async function writeFixtureInstaller(stage, platform) {
  if (platform === "windows-x64") {
    await fs.writeFile(path.join(stage, "install.ps1"), [
      "param([string]$Prefix, [switch]$Setup, [string]$Hosts)",
      '$ErrorActionPreference = "Stop"',
      "New-Item -ItemType Directory -Force -Path $Prefix | Out-Null",
      "if ($Hosts) { Set-Content -Encoding ASCII -Path (Join-Path $Prefix 'installed.txt') -Value ('host=' + $Hosts) } elseif ($Setup) { Set-Content -Encoding ASCII -Path (Join-Path $Prefix 'installed.txt') -Value 'setup' } else { Set-Content -Encoding ASCII -Path (Join-Path $Prefix 'installed.txt') -Value 'install' }"
    ].join("\n"), "utf8");
    return;
  }
  const installer = path.join(stage, "install.sh");
  await fs.writeFile(installer, [
    "#!/usr/bin/env sh",
    "set -eu",
    "target=",
    "result=install",
    "while test \"$#\" -gt 0; do",
    "  case \"$1\" in",
    "    --prefix) target=$2; shift 2 ;;",
    "    --host) result=host=$2; shift 2 ;;",
    "    --setup) result=setup; shift ;;",
    "    *) exit 2 ;;",
    "  esac",
    "done",
    "mkdir -p \"$target\"",
    "printf '%s\\n' \"$result\" > \"$target/installed.txt\""
  ].join("\n"), "utf8");
  await fs.chmod(installer, 0o755);
}

async function createFixtureArchive(stage, archive, platform) {
  const parent = path.dirname(stage);
  if (platform === "windows-x64") {
    const command = `Compress-Archive -LiteralPath '${stage.replace(/'/g, "''")}' -DestinationPath '${archive.replace(/'/g, "''")}' -Force`;
    const result = runCommand("powershell.exe", ["-NoProfile", "-Command", command], parent, process.env);
    assert.equal(result.status, 0, result.stderr);
    return;
  }
  const result = runCommand("tar", ["-C", parent, "-czf", archive, path.basename(stage)], parent, process.env);
  assert.equal(result.status, 0, result.stderr);
}

function runAdapter(argumentsList, environment) {
  return runCommand(process.execPath, [adapterBin, ...argumentsList], packageDirectory, environment);
}

function runCommand(command, argumentsList, cwd, environment, options = {}) {
  return spawnSync(command, argumentsList, { cwd, env: environment, encoding: "utf8", ...options });
}

function npmCommand() {
  return process.platform === "win32" ? "npm.cmd" : "npm";
}
