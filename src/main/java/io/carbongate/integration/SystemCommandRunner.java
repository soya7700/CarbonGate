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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class SystemCommandRunner implements CommandRunner {
    private static final int OUTPUT_LIMIT = 64 * 1024;
    private final Duration timeout;

    public SystemCommandRunner() {
        this(Duration.ofSeconds(15));
    }

    SystemCommandRunner(Duration timeout) {
        this.timeout = timeout;
    }

    @Override
    public boolean available(String executable) {
        String path = System.getenv("PATH");
        if (path == null || path.isBlank()) return false;
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
                if (Files.isRegularFile(candidate) && (Files.isExecutable(candidate) || names.size() > 1)) return true;
            }
        }
        return false;
    }

    @Override
    public Result run(List<String> command) throws IOException, InterruptedException {
        if (command.isEmpty()) throw new IllegalArgumentException("Command cannot be empty");
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
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
}
