package io.carbongate.audit;

import io.carbongate.json.Json;
import io.carbongate.model.Action;
import io.carbongate.model.Decision;
import io.carbongate.model.Evaluation;
import io.carbongate.model.RiskLevel;
import io.carbongate.security.SecretScanner;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/** Detailed, explicitly selected audit sink for enterprise Java services. */
public final class EnterpriseAuditLog implements AuditSink {
    public static final long DEFAULT_DAILY_LIMIT_BYTES = 100_000_000L;
    private static final int MAX_EVENT_BYTES = 32_768;
    private static final Object JVM_WRITE_LOCK = new Object();
    private final Path directory;
    private final long dailyLimitBytes;
    private final SecretScanner secrets = new SecretScanner();
    private final AtomicBoolean failureReported = new AtomicBoolean();

    public EnterpriseAuditLog(Path directory) {
        this(directory, DEFAULT_DAILY_LIMIT_BYTES);
    }

    public EnterpriseAuditLog(Path directory, long dailyLimitBytes) {
        if (dailyLimitBytes < 1) throw new IllegalArgumentException("Daily audit limit must be positive");
        this.directory = directory.toAbsolutePath().normalize();
        this.dailyLimitBytes = dailyLimitBytes;
    }

    @Override
    public boolean recordDecision(Action action, Evaluation evaluation) {
        Map<String, Object> event = base("decision", level(evaluation));
        event.put("id", evaluation.id());
        event.put("actor", sanitize(action.actor(), 256));
        event.put("capability", action.capability().name().toLowerCase());
        event.put("operation", sanitize(action.operation(), 256));
        event.put("resource", truncate(evaluation.sanitizedResource(), 8_192));
        event.put("workspace", sanitize(action.workspace().toString(), 2_048));
        event.put("context", sanitizeContext(action.context()));
        event.put("decision", evaluation.decision().name().toLowerCase());
        event.put("risk", evaluation.risk().name().toLowerCase());
        event.put("reason", sanitize(evaluation.reason(), 2_048));
        event.put("findings", evaluation.findings().stream().limit(20)
                .map(value -> sanitize(value, 512)).toList());
        return append(event);
    }

    @Override
    public boolean recordError(String component, String message) {
        Map<String, Object> event = base("internal_error", "ERROR");
        event.put("component", truncate(component, 256));
        event.put("message", sanitize(message, 4_096));
        return append(event);
    }

    @Override
    public boolean recordControl(String type, Map<String, Object> details) {
        Map<String, Object> event = base("control", "INFO");
        event.put("controlType", truncate(type, 256));
        Map<String, Object> sanitized = new LinkedHashMap<>();
        details.forEach((key, value) -> sanitized.put(truncate(key, 128),
                sanitize(String.valueOf(value), 1_024)));
        event.put("details", sanitized);
        return append(event);
    }

    @Override
    public boolean requiresSuccessfulDecisionRecord() {
        return true;
    }

    public Path todayPath() {
        return path(LocalDate.now());
    }

    public long todayBytes() {
        return size(todayPath());
    }

    public long dailyLimitBytes() {
        return dailyLimitBytes;
    }

    public List<Map<String, Object>> recentBlocked(int requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit, 100));
        Path path = todayPath();
        if (!Files.isRegularFile(path)) return List.of();
        try (var lines = Files.lines(path, StandardCharsets.UTF_8)) {
            ArrayDeque<Map<String, Object>> recent = new ArrayDeque<>(limit);
            for (String line : (Iterable<String>) lines::iterator) {
                try {
                    Map<String, Object> event = Json.object(line);
                    if ("deny".equals(event.get("decision"))) {
                        if (recent.size() == limit) recent.removeFirst();
                        recent.addLast(event);
                    }
                } catch (IllegalArgumentException ignored) {
                    // Ignore a corrupt or partial line without amplifying it.
                }
            }
            List<Map<String, Object>> result = new ArrayList<>(recent.size());
            while (!recent.isEmpty()) result.add(recent.removeLast());
            return List.copyOf(result);
        } catch (IOException error) {
            return List.of();
        }
    }

    public Stats todayStats() {
        long allow = 0;
        long ask = 0;
        long deny = 0;
        long errors = 0;
        Path path = todayPath();
        if (Files.isRegularFile(path)) {
            try (var lines = Files.lines(path, StandardCharsets.UTF_8)) {
                for (String line : (Iterable<String>) lines::iterator) {
                    try {
                        Map<String, Object> event = Json.object(line);
                        String decision = String.valueOf(event.get("decision"));
                        if (decision.equals("allow")) allow++;
                        else if (decision.equals("ask")) ask++;
                        else if (decision.equals("deny")) deny++;
                        if ("internal_error".equals(event.get("type"))) errors++;
                    } catch (IllegalArgumentException ignored) {
                        // ignored
                    }
                }
            } catch (IOException ignored) {
                // Return the counts collected so far.
            }
        }
        return new Stats(allow, ask, deny, errors, todayBytes(), dailyLimitBytes);
    }

    private boolean append(Map<String, Object> event) {
        byte[] bytes = (Json.stringify(event) + "\n").getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_EVENT_BYTES) return false;
        try {
            synchronized (JVM_WRITE_LOCK) {
                Files.createDirectories(directory);
                LocalDate today = LocalDate.now();
                try (FileChannel lockChannel = FileChannel.open(directory.resolve(".write.lock"),
                        StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                     FileLock lock = lockChannel.lock()) {
                    if (!lock.isValid()) return false;
                    Path target = path(today);
                    if (size(target) + bytes.length > dailyLimitBytes) return false;
                    try (FileChannel output = FileChannel.open(target, StandardOpenOption.CREATE,
                            StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
                        ByteBuffer buffer = ByteBuffer.wrap(bytes);
                        while (buffer.hasRemaining()) output.write(buffer);
                    }
                    return true;
                }
            }
        } catch (IOException error) {
            if (failureReported.compareAndSet(false, true)) {
                System.err.println("CarbonGate enterprise audit error: " + error.getMessage());
            }
            return false;
        }
    }

    private Map<String, Object> base(String type, String level) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("at", Instant.now().toString());
        event.put("level", level);
        event.put("type", type);
        return event;
    }

    private String level(Evaluation evaluation) {
        if (evaluation.decision() == Decision.DENY) return "ERROR";
        if (evaluation.decision() == Decision.ASK || evaluation.risk().atLeast(RiskLevel.HIGH)) return "WARN";
        return "INFO";
    }

    private Map<String, Object> sanitizeContext(Map<String, String> context) {
        Map<String, Object> result = new LinkedHashMap<>();
        context.forEach((key, value) -> {
            String normalized = key.toLowerCase();
            boolean sensitiveKey = normalized.contains("password") || normalized.contains("passwd") ||
                    normalized.contains("secret") || normalized.contains("token") ||
                    normalized.contains("apikey") || normalized.contains("api_key");
            result.put(truncate(key, 128), sensitiveKey ? "<SECRET:CONTEXT>" : sanitize(value, 1_024));
        });
        return result;
    }

    private String sanitize(String value, int limit) {
        return truncate(secrets.scan(value == null ? "" : value).redacted(), limit);
    }

    private Path path(LocalDate day) {
        return directory.resolve("audit-" + day + ".jsonl");
    }

    private long size(Path path) {
        try {
            return Files.isRegularFile(path) ? Files.size(path) : 0L;
        } catch (IOException ignored) {
            return 0L;
        }
    }

    private String truncate(String value, int limit) {
        if (value == null) return "";
        return value.length() <= limit ? value : value.substring(0, limit) + "…";
    }

    public record Stats(long allowed, long pendingApproval, long denied,
                        long internalErrors, long bytesWritten, long byteLimit) {}
}
