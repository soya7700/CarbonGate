package io.carbongate.sandbox.container;

import io.carbongate.json.Json;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/** Docker/Podman adapter with a fixed fail-closed container security profile. */
public final class ContainerSandboxProvider {
    private static final String API_VERSION = "carbongate.provider/v1";
    private static final Pattern DIGEST_IMAGE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._/:\\-]{0,255}@sha256:[a-f0-9]{64}");
    private static final int MAX_STDOUT = 262_144;
    private static final int MAX_STDERR = 65_536;

    private ContainerSandboxProvider() {}

    public static void main(String[] args) throws Exception {
        byte[] input = System.in.readNBytes(1_048_577);
        if (input.length > 1_048_576) throw new IllegalArgumentException("request exceeds 1 MiB");
        Map<String, Object> request = Json.object(new String(input, StandardCharsets.UTF_8).trim());
        String id = string(request.get("id"), "request id");
        try {
            String operation = string(request.get("operation"), "operation");
            Map<String, Object> result = switch (operation) {
                case "health" -> health();
                case "sandbox" -> execute(SandboxRequest.from(object(request.get("payload"), "payload")));
                default -> throw new IllegalArgumentException("Unsupported operation");
            };
            respond(id, "ok", result);
        } catch (IOException | IllegalArgumentException error) {
            respond(id, "error", Map.of("code", "sandbox_unavailable", "message", compact(error)));
        }
    }

    static List<String> command(String runtime, SandboxRequest request, String containerName) {
        List<String> command = new ArrayList<>();
        command.add(runtime);
        command.addAll(List.of("run", "--rm", "--name=" + containerName, "--pull=never", "--network=none", "--read-only",
                "--cap-drop=ALL", "--security-opt=no-new-privileges", "--pids-limit=128",
                "--memory=" + request.memoryMb() + "m", "--cpus=" + request.cpus(),
                "--user=65532:65532", "--tmpfs=/tmp:rw,noexec,nosuid,nodev,size=64m",
                "--env=HOME=/tmp", "--env=TMPDIR=/tmp", "--env=LANG=C.UTF-8",
                "--workdir=/workspace"));
        String mount = "--mount=type=bind,source=" + request.workspace() + ",target=/workspace";
        command.add(request.writable() ? mount : mount + ",readonly");
        command.add(request.image());
        command.addAll(request.command());
        return List.copyOf(command);
    }

    private static Map<String, Object> health() throws IOException, InterruptedException {
        String runtime = findRuntime();
        return Map.of("health", "pass", "runtime", runtime, "profile", "container-default-deny-v1");
    }

    private static Map<String, Object> execute(SandboxRequest request) throws IOException, InterruptedException {
        String runtime = findRuntime();
        String containerName = "carbongate-" + UUID.randomUUID().toString().replace("-", "");
        List<String> command = command(runtime, request, containerName);
        Process process = new ProcessBuilder(command).start();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var stdout = executor.submit(() -> drain(process.getInputStream(), MAX_STDOUT));
            var stderr = executor.submit(() -> drain(process.getErrorStream(), MAX_STDERR));
            if (!process.waitFor(request.timeoutMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                cleanup(runtime, containerName);
                throw new IOException("Sandbox execution timed out");
            }
            Captured out = get(stdout);
            Captured err = get(stderr);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("exitCode", process.exitValue());
            result.put("stdout", out.value());
            result.put("stderr", err.value());
            result.put("stdoutTruncated", out.truncated());
            result.put("stderrTruncated", err.truncated());
            result.put("runtime", runtime);
            result.put("profile", "container-default-deny-v1");
            result.put("network", "none");
            result.put("workspaceWritable", request.writable());
            return Map.copyOf(result);
        }
    }

    private static void cleanup(String runtime, String containerName) throws InterruptedException {
        try {
            Process cleanup = new ProcessBuilder(runtime, "rm", "-f", containerName)
                    .redirectErrorStream(true).start();
            if (!cleanup.waitFor(3, TimeUnit.SECONDS)) cleanup.destroyForcibly();
        } catch (IOException ignored) {
            // The original timeout remains the primary fail-closed result.
        }
    }

    private static String findRuntime() throws IOException, InterruptedException {
        for (String runtime : List.of("docker", "podman")) {
            try {
                Process process = new ProcessBuilder(runtime, "--version").redirectErrorStream(true).start();
                if (process.waitFor(2, TimeUnit.SECONDS) && process.exitValue() == 0) return runtime;
                process.destroyForcibly();
            } catch (IOException ignored) {
                // Try the next fixed runtime name. Payloads cannot choose an executable.
            }
        }
        throw new IOException("Neither Docker nor Podman is available");
    }

    private static Captured drain(InputStream input, int limit) throws IOException {
        ByteArrayOutputStream retained = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        boolean truncated = false;
        int count;
        while ((count = input.read(buffer)) >= 0) {
            int keep = Math.min(count, Math.max(0, limit - retained.size()));
            if (keep > 0) retained.write(buffer, 0, keep);
            if (keep < count) truncated = true;
        }
        return new Captured(retained.toString(StandardCharsets.UTF_8), truncated);
    }

    private static Captured get(java.util.concurrent.Future<Captured> future) throws IOException, InterruptedException {
        try {
            return future.get();
        } catch (ExecutionException error) {
            throw new IOException("Failed to capture container output", error.getCause());
        }
    }

    private static void respond(String id, String status, Map<String, Object> result) {
        System.out.println(Json.stringify(Map.of("apiVersion", API_VERSION, "id", id,
                "status", status, status.equals("ok") ? "result" : "error", result)));
    }

    record SandboxRequest(Path workspace, String image, List<String> command, boolean writable,
                          int timeoutMillis, int memoryMb, double cpus) {
        SandboxRequest {
            workspace = workspace.toAbsolutePath().normalize();
            if (!Files.isDirectory(workspace) || workspace.getNameCount() < 2 || workspace.toString().contains(",")) {
                throw new IllegalArgumentException("workspace must be a safe existing non-root directory without commas");
            }
            try {
                workspace = workspace.toRealPath();
            } catch (IOException error) {
                throw new IllegalArgumentException("workspace cannot be resolved", error);
            }
            if (!DIGEST_IMAGE.matcher(image).matches()) {
                throw new IllegalArgumentException("image must be pinned by sha256 digest");
            }
            command = List.copyOf(command);
            if (command.isEmpty() || command.size() > 64 || command.stream().anyMatch(SandboxRequest::unsafeToken)) {
                throw new IllegalArgumentException("command requires 1 to 64 safe argument tokens");
            }
            if (timeoutMillis < 100 || timeoutMillis > 20_000) throw new IllegalArgumentException("timeoutMillis must be 100..20000");
            if (memoryMb < 64 || memoryMb > 4096) throw new IllegalArgumentException("memoryMb must be 64..4096");
            if (!Double.isFinite(cpus) || cpus < 0.1 || cpus > 4.0) throw new IllegalArgumentException("cpus must be 0.1..4.0");
        }

        static SandboxRequest from(Map<String, Object> value) {
            Object rawCommand = value.get("command");
            if (!(rawCommand instanceof List<?> values)) throw new IllegalArgumentException("command must be an array");
            List<String> command = new ArrayList<>();
            for (Object item : values) command.add(string(item, "command argument"));
            String network = value.getOrDefault("network", "none").toString().toLowerCase(Locale.ROOT);
            if (!network.equals("none")) throw new IllegalArgumentException("v1 sandbox network must be none");
            return new SandboxRequest(Path.of(string(value.get("workspace"), "workspace")),
                    string(value.get("image"), "image"), command, booleanValue(value.get("writable"), false),
                    integer(value.get("timeoutMillis"), 10_000), integer(value.get("memoryMb"), 256),
                    number(value.get("cpus"), 1.0));
        }

        private static boolean unsafeToken(String value) {
            return value == null || value.isBlank() || value.length() > 4096 || value.indexOf('\0') >= 0
                    || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0;
        }
    }

    private record Captured(String value, boolean truncated) {}

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value, String name) {
        if (value instanceof Map<?, ?> map) return (Map<String, Object>) map;
        throw new IllegalArgumentException(name + " must be an object");
    }

    private static String string(Object value, String name) {
        if (value instanceof String text && !text.isBlank()) return text;
        throw new IllegalArgumentException(name + " is required");
    }

    private static int integer(Object value, int fallback) {
        if (value == null) return fallback;
        if (value instanceof Number number) return number.intValue();
        throw new IllegalArgumentException("numeric sandbox value is invalid");
    }

    private static double number(Object value, double fallback) {
        if (value == null) return fallback;
        if (value instanceof Number number) return number.doubleValue();
        throw new IllegalArgumentException("numeric sandbox value is invalid");
    }

    private static boolean booleanValue(Object value, boolean fallback) {
        if (value == null) return fallback;
        if (value instanceof Boolean flag) return flag;
        throw new IllegalArgumentException("writable must be true or false");
    }

    private static String compact(Throwable error) {
        String value = error.getMessage();
        if (value == null || value.isBlank()) value = error.getClass().getSimpleName();
        value = value.replace('\n', ' ').replace('\r', ' ').trim();
        return value.substring(0, Math.min(value.length(), 256));
    }
}
