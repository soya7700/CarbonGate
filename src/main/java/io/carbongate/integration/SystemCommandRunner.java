package io.carbongate.integration;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class SystemCommandRunner implements CommandRunner {
    private static final int OUTPUT_LIMIT = 64 * 1024;
    private final Duration timeout;
    private final Map<String, String> environment;

    public SystemCommandRunner() {
        this(Duration.ofSeconds(15), System.getenv());
    }

    SystemCommandRunner(Duration timeout) {
        this(timeout, System.getenv());
    }

    SystemCommandRunner(Duration timeout, Map<String, String> environment) {
        this.timeout = timeout;
        this.environment = Map.copyOf(environment);
    }

    @Override
    public boolean available(String executable) {
        return resolveExecutable(executable) != null;
    }

    Path resolveExecutable(String executable) {
        if (executable == null || executable.isBlank()) return null;
        Path direct = Path.of(executable);
        if (direct.isAbsolute() && executableFile(direct)) return direct;

        Path configuredCodex = configuredCodex(executable);
        if (configuredCodex != null) return configuredCodex;

        String path = environment.get("PATH");
        if (path == null || path.isBlank()) return null;
        List<String> names = new ArrayList<>(List.of(executable));
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            names.add(executable + ".exe");
            names.add(executable + ".cmd");
            names.add(executable + ".bat");
        }
        for (String directory : path.split(java.util.regex.Pattern.quote(java.io.File.pathSeparator))) {
            if (directory.isBlank()) continue;
            for (String name : names) {
                Path candidate = Path.of(directory).resolve(name);
                if (executableFile(candidate)) return candidate;
            }
        }
        return null;
    }

    @Override
    public Result run(List<String> command) throws IOException, InterruptedException {
        if (command.isEmpty()) throw new IllegalArgumentException("Command cannot be empty");
        List<String> resolvedCommand = new ArrayList<>(command);
        Path executable = resolveExecutable(command.getFirst());
        if (executable != null) resolvedCommand.set(0, executable.toString());
        Process process = new ProcessBuilder(resolvedCommand).redirectErrorStream(true).start();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<String> output = executor.submit(() -> readBounded(process.getInputStream()));
            boolean completed = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!completed) {
                process.destroyForcibly();
                process.waitFor(2, TimeUnit.SECONDS);
            }
            try {
                return new Result(completed ? process.exitValue() : -1, output.get(), !completed);
            } catch (ExecutionException error) {
                Throwable cause = error.getCause();
                if (cause instanceof IOException io) throw io;
                throw new IOException("Unable to read integration command output", cause);
            }
        }
    }

    private String readBounded(InputStream input) throws IOException {
        ByteArrayOutputStream retained = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = input.read(buffer)) >= 0) {
            int remaining = OUTPUT_LIMIT - retained.size();
            if (remaining > 0) retained.write(buffer, 0, Math.min(read, remaining));
        }
        return retained.toString(StandardCharsets.UTF_8);
    }

    private Path configuredCodex(String executable) {
        if (!executable.equals("codex")) return null;
        String configured = environment.get("CODEX_CLI_PATH");
        if (configured != null && !configured.isBlank()) {
            Path candidate = Path.of(configured);
            if (executableFile(candidate)) return candidate;
        }
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac")) return null;
        List<Path> candidates = List.of(
                Path.of("/Applications/ChatGPT.app/Contents/Resources/codex"),
                Path.of(System.getProperty("user.home"), "Applications", "ChatGPT.app", "Contents", "Resources", "codex"));
        return candidates.stream().filter(this::executableFile).findFirst().orElse(null);
    }

    private boolean executableFile(Path candidate) {
        return Files.isRegularFile(candidate) && (Files.isExecutable(candidate)
                || System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win"));
    }
}
