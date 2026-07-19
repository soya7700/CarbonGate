import { createHash } from "node:crypto";
import { spawn } from "node:child_process";
import { promises as fs } from "node:fs";
import https from "node:https";
import os from "node:os";
import path from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

const PACKAGE_DIRECTORY = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const MANIFEST_PATH = path.join(PACKAGE_DIRECTORY, "distribution", "release-assets.properties");
const PACKAGE_JSON_PATH = path.join(PACKAGE_DIRECTORY, "package.json");
const MAX_DOWNLOAD_BYTES = 256 * 1024 * 1024;
const VERSION_PATTERN = /^[0-9]+\.[0-9]+\.[0-9]+(?:[+-][A-Za-z0-9.-]+)?$/;
const GITHUB_API_ORIGIN = "https://api.github.com";
const GITHUB_WEB_ORIGIN = "https://github.com";

export async function main(argv, environment = process.env) {
  const command = argv[0] ?? "help";
  if (command === "help" || command === "--help" || command === "-h") {
    printHelp();
    return;
  }
  if (command === "version" || command === "--version" || command === "-v") {
    const packageJson = JSON.parse(await fs.readFile(PACKAGE_JSON_PATH, "utf8"));
    process.stdout.write(`@carbongate/cli ${packageJson.version}\n`);
    return;
  }
  if (command !== "setup" && command !== "install") {
    throw new Error(`unknown command: ${command}`);
  }

  const options = parseSetupOptions(argv.slice(1), environment);
  if (command === "install" && options.hosts) {
    throw new Error("--host is only valid with setup");
  }
  options.configureHosts = command === "setup";
  await installRelease(options, environment);
}

function printHelp() {
  process.stdout.write([
    "Usage: npx @carbongate/cli <install|setup> [--version VERSION] [--prefix PATH] [--host HOSTS]",
    "",
    "install downloads and verifies one native release without changing Agent host configuration.",
    "setup does the same and explicitly configures selected or detected Agent hosts.",
    "No download happens when this npm package is installed."
  ].join("\n") + "\n");
}

export function parseSetupOptions(argumentsList, environment) {
  const options = { version: environment.CARBONGATE_VERSION ?? "", prefix: "", hosts: "" };
  for (let index = 0; index < argumentsList.length; index += 1) {
    const argument = argumentsList[index];
    if (argument === "--version") {
      options.version = requiredValue(argumentsList, ++index, "--version");
    } else if (argument === "--prefix") {
      options.prefix = requiredValue(argumentsList, ++index, "--prefix");
    } else if (argument === "--host") {
      options.hosts = requiredValue(argumentsList, ++index, "--host");
    } else if (argument === "--help" || argument === "-h") {
      printHelp();
      return { help: true };
    } else {
      throw new Error(`unknown setup option: ${argument}`);
    }
  }
  if (options.version && !VERSION_PATTERN.test(options.version)) {
    throw new Error(`invalid CarbonGate release version: ${options.version}`);
  }
  return options;
}

function requiredValue(argumentsList, index, option) {
  const value = argumentsList[index];
  if (!value || value.startsWith("--")) {
    throw new Error(`${option} requires a value`);
  }
  return value;
}

export async function installRelease(options, environment) {
  if (options.help) {
    return;
  }
  const manifest = parseManifest(await fs.readFile(MANIFEST_PATH, "utf8"));
  const platform = supportedPlatform();
  const version = options.version || await latestVersion(manifest.repository, environment);
  if (!VERSION_PATTERN.test(version)) {
    throw new Error(`invalid CarbonGate release version: ${version}`);
  }
  const assetPattern = manifest[`native.${platform}.asset`];
  if (!assetPattern) {
    throw new Error(`the release manifest does not support ${platform}`);
  }
  const asset = expandAsset(assetPattern, version);
  const releaseBase = environment.CARBONGATE_RELEASE_BASE_URL
    ?? `https://github.com/${manifest.repository}/releases/download`;
  const archiveUrl = releaseAssetUrl(releaseBase, version, asset);
  const checksumUrl = releaseAssetUrl(releaseBase, version, `${asset}.sha256`);
  const workDirectory = await fs.mkdtemp(path.join(os.tmpdir(), "carbongate-npm-"));

  try {
    const archivePath = path.join(workDirectory, asset);
    const checksumPath = `${archivePath}.sha256`;
    await writeResource(archiveUrl, archivePath, environment);
    await writeResource(checksumUrl, checksumPath, environment);
    await verifyChecksum(archivePath, checksumPath, asset);
    await extractArchive(archivePath, workDirectory, platform);
    await runBundledInstaller(workDirectory, asset, platform, options);
    process.stdout.write(`Installed verified CarbonGate ${version} release through npm adapter.\n`);
  } finally {
    await fs.rm(workDirectory, { recursive: true, force: true });
  }
}

export function parseManifest(contents) {
  const values = Object.create(null);
  for (const rawLine of contents.split(/\r?\n/)) {
    const line = rawLine.trim();
    if (!line || line.startsWith("#")) {
      continue;
    }
    const separator = line.indexOf("=");
    if (separator <= 0) {
      throw new Error("malformed CarbonGate release manifest");
    }
    const key = line.slice(0, separator);
    const value = line.slice(separator + 1);
    if (!value || Object.hasOwn(values, key)) {
      throw new Error("malformed CarbonGate release manifest");
    }
    values[key] = value;
  }
  if (values["schema.version"] !== "1" || !values.repository) {
    throw new Error("unsupported CarbonGate release manifest schema");
  }
  return values;
}

export function supportedPlatform(platform = process.platform, architecture = process.arch) {
  const platformMap = {
    darwin: { arm64: "darwin-arm64" },
    linux: { x64: "linux-x64" },
    win32: { x64: "windows-x64" }
  };
  const resolved = platformMap[platform]?.[architecture];
  if (!resolved) {
    throw new Error(`no native CarbonGate release for ${platform}/${architecture}; use the portable Java 21 archive`);
  }
  return resolved;
}

function expandAsset(pattern, version) {
  if (!pattern.includes("{version}")) {
    throw new Error("release asset pattern has no version token");
  }
  const asset = pattern.replace("{version}", version);
  if (!/^[A-Za-z0-9][A-Za-z0-9._-]+$/.test(asset) || asset.includes("..")) {
    throw new Error("unsafe release asset name");
  }
  return asset;
}

async function latestVersion(repository, environment) {
  const defaultSource = `${GITHUB_API_ORIGIN}/repos/${repository}/releases/latest`;
  const source = environment.CARBONGATE_LATEST_URL ?? defaultSource;
  try {
    return parseLatestReleasePayload(await readResource(source, environment));
  } catch (error) {
    if (source !== defaultSource || !isGitHubRateLimit(error)) {
      throw error;
    }
    return latestVersionFromGitHubRedirect(repository);
  }
}

function parseLatestReleasePayload(resource) {
  const payload = JSON.parse(resource.toString("utf8"));
  const tag = String(payload.tag_name ?? "");
  const version = tag.startsWith("v") ? tag.slice(1) : tag;
  if (!VERSION_PATTERN.test(version)) {
    throw new Error("latest CarbonGate release response has no valid semantic version");
  }
  return version;
}

async function latestVersionFromGitHubRedirect(repository) {
  const destination = await readRedirectTarget(`${GITHUB_WEB_ORIGIN}/${repository}/releases/latest`);
  return versionFromGitHubReleaseRedirect(repository, destination);
}

function versionFromGitHubReleaseRedirect(repository, destination) {
  const url = new URL(destination);
  const prefix = `/${repository}/releases/tag/`;
  if (url.origin !== GITHUB_WEB_ORIGIN || !url.pathname.startsWith(prefix)) {
    throw new Error("GitHub latest-release redirect did not resolve to the expected repository");
  }
  const tag = decodeURIComponent(url.pathname.slice(prefix.length));
  if (!tag || tag.includes("/")) {
    throw new Error("GitHub latest-release redirect has an unsafe tag");
  }
  const version = tag.startsWith("v") ? tag.slice(1) : tag;
  if (!VERSION_PATTERN.test(version)) {
    throw new Error("GitHub latest-release redirect has no valid semantic version");
  }
  return version;
}

function isGitHubRateLimit(error) {
  return error?.statusCode === 403
    && error.headers?.["x-ratelimit-remaining"] === "0";
}

function releaseAssetUrl(base, version, asset) {
  const normalizedBase = base.endsWith("/") ? base : `${base}/`;
  return new URL(`v${version}/${asset}`, normalizedBase).toString();
}

async function writeResource(resource, destination, environment) {
  await fs.writeFile(destination, await readResource(resource, environment));
}

async function readResource(resource, environment, redirects = 0) {
  const url = new URL(resource);
  if (url.protocol === "file:") {
    if (environment.CARBONGATE_ALLOW_FILE_URLS !== "1") {
      throw new Error("file:// sources require CARBONGATE_ALLOW_FILE_URLS=1");
    }
    return fs.readFile(fileURLToPath(url));
  }
  if (url.protocol !== "https:") {
    throw new Error(`refusing non-HTTPS resource: ${resource}`);
  }
  if (redirects > 5) {
    throw new Error("too many HTTPS redirects while downloading CarbonGate");
  }
  return new Promise((resolve, reject) => {
    const request = https.get(url, { headers: { "user-agent": "CarbonGate-npm-adapter" } }, (response) => {
      const status = response.statusCode ?? 0;
      if (status >= 300 && status < 400 && response.headers.location) {
        response.resume();
        readResource(new URL(response.headers.location, url).toString(), environment, redirects + 1)
          .then(resolve, reject);
        return;
      }
      if (status < 200 || status >= 300) {
        response.resume();
        reject(new HttpStatusError(status, response.headers, `download failed with HTTP ${status}: ${url}`));
        return;
      }
      const chunks = [];
      let length = 0;
      response.on("data", (chunk) => {
        length += chunk.length;
        if (length > MAX_DOWNLOAD_BYTES) {
          request.destroy(new Error("CarbonGate download exceeds the 256 MiB adapter limit"));
          return;
        }
        chunks.push(chunk);
      });
      response.on("end", () => resolve(Buffer.concat(chunks)));
    });
    request.on("error", reject);
  });
}

async function readRedirectTarget(resource) {
  const url = new URL(resource);
  if (url.protocol !== "https:") {
    throw new Error(`refusing non-HTTPS latest-release redirect: ${resource}`);
  }
  return new Promise((resolve, reject) => {
    const request = https.get(url, { headers: { "user-agent": "CarbonGate-npm-adapter" } }, (response) => {
      const status = response.statusCode ?? 0;
      if (status >= 300 && status < 400 && response.headers.location) {
        response.resume();
        resolve(new URL(response.headers.location, url).toString());
        return;
      }
      response.resume();
      reject(new HttpStatusError(status, response.headers,
        `latest-release redirect failed with HTTP ${status}: ${url}`));
    });
    request.on("error", reject);
  });
}

class HttpStatusError extends Error {
  constructor(statusCode, headers, message) {
    super(message);
    this.name = "HttpStatusError";
    this.statusCode = statusCode;
    this.headers = headers;
  }
}

async function verifyChecksum(archivePath, checksumPath, asset) {
  const checksum = (await fs.readFile(checksumPath, "utf8")).trim();
  const match = /^([A-Fa-f0-9]{64})\s+\*?([^\r\n]+)$/.exec(checksum);
  if (!match || match[2] !== asset) {
    throw new Error("malformed CarbonGate checksum file");
  }
  const actual = createHash("sha256").update(await fs.readFile(archivePath)).digest("hex");
  if (actual !== match[1].toLowerCase()) {
    throw new Error("CarbonGate release checksum verification failed");
  }
}

async function extractArchive(archivePath, workDirectory, platform) {
  if (platform === "windows-x64") {
    await extractZipSafely(archivePath, workDirectory);
    return;
  }
  const listing = await runCapture("tar", ["-tzf", archivePath]);
  if (listing.split(/\r?\n/).some((entry) => entry.startsWith("/") || entry.split("/").includes(".."))) {
    throw new Error("release archive contains an unsafe path");
  }
  await run("tar", ["-xzf", archivePath, "-C", workDirectory]);
}

async function extractZipSafely(archivePath, workDirectory) {
  const extractorPath = path.join(workDirectory, "extract-release.ps1");
  const extractor = [
    "param([string]$ZipPath, [string]$DestinationPath)",
    '$ErrorActionPreference = "Stop"',
    "Add-Type -AssemblyName System.IO.Compression.FileSystem",
    "$root = [IO.Path]::GetFullPath($DestinationPath + [IO.Path]::DirectorySeparatorChar)",
    "$zip = [IO.Compression.ZipFile]::OpenRead($ZipPath)",
    "try { foreach ($entry in $zip.Entries) { $destination = [IO.Path]::GetFullPath((Join-Path $DestinationPath $entry.FullName)); if (-not $destination.StartsWith($root, [StringComparison]::OrdinalIgnoreCase)) { throw 'Release archive contains an unsafe path.' } } } finally { $zip.Dispose() }",
    "Expand-Archive -LiteralPath $ZipPath -DestinationPath $DestinationPath -Force"
  ].join("\n");
  await fs.writeFile(extractorPath, extractor, "utf8");
  await run("powershell.exe", [
    "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", extractorPath,
    "-ZipPath", archivePath, "-DestinationPath", workDirectory
  ]);
}

async function runBundledInstaller(workDirectory, asset, platform, options) {
  const packageDirectory = path.join(workDirectory, asset.replace(/\.(tar\.gz|zip)$/, ""));
  if (platform === "windows-x64") {
    const installer = path.join(packageDirectory, "install.ps1");
    await assertFile(installer, "release archive does not contain install.ps1");
    const argumentsList = ["-NoProfile", "-ExecutionPolicy", "Bypass", "-File", installer];
    if (options.prefix) argumentsList.push("-Prefix", options.prefix);
    if (options.configureHosts) {
      if (options.hosts) argumentsList.push("-Hosts", options.hosts);
      else argumentsList.push("-Setup");
    }
    await run("powershell.exe", argumentsList);
    return;
  }
  const installer = path.join(packageDirectory, "install.sh");
  await assertFile(installer, "release archive does not contain install.sh");
  const argumentsList = [installer];
  if (options.prefix) argumentsList.push("--prefix", options.prefix);
  if (options.configureHosts) {
    if (options.hosts) argumentsList.push("--host", options.hosts);
    else argumentsList.push("--setup");
  }
  await run("sh", argumentsList);
}

async function assertFile(filePath, message) {
  try {
    const stat = await fs.stat(filePath);
    if (!stat.isFile()) throw new Error(message);
  } catch {
    throw new Error(message);
  }
}

function run(program, argumentsList) {
  return new Promise((resolve, reject) => {
    const child = spawn(program, argumentsList, { stdio: "inherit" });
    child.on("error", (error) => reject(new Error(`${program} could not run: ${error.message}`)));
    child.on("close", (code) => {
      if (code === 0) resolve();
      else reject(new Error(`${program} exited with code ${code}`));
    });
  });
}

function runCapture(program, argumentsList) {
  return new Promise((resolve, reject) => {
    const child = spawn(program, argumentsList, { stdio: ["ignore", "pipe", "pipe"] });
    const stdout = [];
    const stderr = [];
    child.stdout.on("data", (chunk) => stdout.push(chunk));
    child.stderr.on("data", (chunk) => stderr.push(chunk));
    child.on("error", (error) => reject(new Error(`${program} could not run: ${error.message}`)));
    child.on("close", (code) => {
      if (code === 0) resolve(Buffer.concat(stdout).toString("utf8"));
      else reject(new Error(`${program} exited with code ${code}: ${Buffer.concat(stderr).toString("utf8").trim()}`));
    });
  });
}

export const testing = {
  expandAsset,
  isGitHubRateLimit,
  releaseAssetUrl,
  verifyChecksum,
  versionFromGitHubReleaseRedirect
};
