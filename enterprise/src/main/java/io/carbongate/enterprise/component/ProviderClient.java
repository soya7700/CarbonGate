package io.carbongate.enterprise.component;

import io.carbongate.json.Json;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class ProviderClient {
    public static final String API_VERSION = "carbongate.provider/v1";
    private static final int MAX_MESSAGE_BYTES = 1_048_576;
    private static final int MAX_STDERR_BYTES = 8_192;

    public Map<String, Object> call(ComponentManifest manifest, Path componentDirectory,
                                    String operation, Map<String, Object> payload)
            throws IOException, InterruptedException {
        if (manifest.kind() == ComponentManifest.Kind.PACK) throw new IllegalArgumentException("Pack cannot run as a Provider");
        if (!operation.equals("health") && !manifest.operations().contains(operation)) {
            throw new IllegalArgumentException("Component does not declare operation: " + operation);
        }
        String id = UUID.randomUUID().toString();
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("apiVersion", API_VERSION);
        request.put("id", id);
        request.put("operation", operation);
        request.put("deadlineMillis", manifest.timeoutMillis());
        request.put("payload", payload == null ? Map.of() : payload);
        byte[] requestBytes = (Json.stringify(request) + "\n").getBytes(StandardCharsets.UTF_8);
        if (requestBytes.length > MAX_MESSAGE_BYTES) throw new IllegalArgumentException("Provider request exceeds 1 MiB");

        List<String> command = manifest.command(componentDirectory);
        ProcessBuilder builder = new ProcessBuilder(command).directory(componentDirectory.toFile());
        Map<String, String> inherited = new LinkedHashMap<>(builder.environment());
        builder.environment().clear();
        copyEnvironment(inherited, builder.environment(), "PATH", "JAVA_HOME", "SystemRoot", "TEMP", "TMP");
        Process process = builder.start();
        long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(manifest.timeoutMillis());
        try (var input = process.getOutputStream()) {
            input.write(requestBytes);
        }

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var responseFuture = executor.submit(() -> readLine(process.getInputStream(), MAX_MESSAGE_BYTES));
            var stderrFuture = executor.submit(() -> drain(process.getErrorStream(), MAX_STDERR_BYTES));
            String responseLine;
            try {
                responseLine = responseFuture.get(remainingMillis(deadlineNanos), TimeUnit.MILLISECONDS);
            } catch (TimeoutException error) {
                process.destroyForcibly();
                throw new IOException("Provider timed out after " + manifest.timeoutMillis() + " ms");
            } catch (ExecutionException error) {
                process.destroyForcibly();
                throw new IOException("Provider response failed", error.getCause());
            }
            if (!process.waitFor(remainingMillis(deadlineNanos), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new IOException("Provider did not exit before its deadline");
            }
            String stderr;
            try {
                stderr = stderrFuture.get(100, TimeUnit.MILLISECONDS);
            } catch (ExecutionException | TimeoutException ignored) {
                stderr = "";
            }
            if (process.exitValue() != 0) {
                throw new IOException("Provider exited with " + process.exitValue() + compact(stderr));
            }
            Map<String, Object> response = Json.object(responseLine);
            if (!API_VERSION.equals(response.get("apiVersion")) || !id.equals(response.get("id"))) {
                throw new IOException("Provider returned an invalid protocol envelope");
            }
            if (!"ok".equals(response.get("status"))) {
                throw new IOException("Provider returned an error status" + compact(stderr));
            }
            return Map.copyOf(response);
        }
    }

    private String readLine(InputStream input, int limit) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int value;
        while ((value = input.read()) >= 0) {
            if (value == '\n') break;
            if (output.size() >= limit) throw new IOException("Provider response exceeds 1 MiB");
            output.write(value);
        }
        if (output.size() == 0 && value < 0) throw new IOException("Provider returned no response");
        return output.toString(StandardCharsets.UTF_8);
    }

    private String drain(InputStream input, int retainedLimit) throws IOException {
        ByteArrayOutputStream retained = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int count;
        while ((count = input.read(buffer)) >= 0) {
            int retain = Math.min(count, Math.max(0, retainedLimit - retained.size()));
            if (retain > 0) retained.write(buffer, 0, retain);
        }
        return retained.toString(StandardCharsets.UTF_8);
    }

    private void copyEnvironment(Map<String, String> source, Map<String, String> target, String... keys) {
        for (String key : keys) {
            String value = source.get(key);
            if (value != null) target.put(key, value);
        }
    }

    private long remainingMillis(long deadlineNanos) {
        long remaining = deadlineNanos - System.nanoTime();
        if (remaining <= 0) return 0;
        return Math.max(1, TimeUnit.NANOSECONDS.toMillis(remaining));
    }

    private String compact(String stderr) {
        if (stderr == null || stderr.isBlank()) return "";
        String value = stderr.replace('\n', ' ').replace('\r', ' ').trim();
        return ": " + value.substring(0, Math.min(value.length(), 256));
    }
}
