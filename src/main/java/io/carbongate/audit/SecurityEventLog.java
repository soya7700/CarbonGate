package io.carbongate.audit;

import io.carbongate.json.Json;
import io.carbongate.model.Action;
import io.carbongate.model.Evaluation;
import io.carbongate.model.Decision;
import io.carbongate.security.SecretScanner;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Minimal error-only event log. ALLOW and ASK decisions are never written.
 * Blocked and internal-error files share one hard daily byte budget.
 */
public final class SecurityEventLog implements AuditSink {
    public static final long DEFAULT_DAILY_LIMIT_BYTES = 10_000_000L;
    private static final int MAX_EVENT_BYTES = 1_024;
    private static final int MAX_RESOURCE_CHARS = 256;
    private static final int MAX_MESSAGE_CHARS = 256;
    private static final Object JVM_WRITE_LOCK = new Object();
    private final Path logDirectory;
    private final long dailyLimitBytes;
    private final SecretScanner secrets = new SecretScanner();
    private final AtomicBoolean writeFailureReported = new AtomicBoolean();

    public SecurityEventLog(Path home) {
        this(home, DEFAULT_DAILY_LIMIT_BYTES);
    }

    public SecurityEventLog(Path home, long dailyLimitBytes) {
        if (dailyLimitBytes < 1) throw new IllegalArgumentException("Daily log limit must be positive");
        this.logDirectory = home.toAbsolutePath().normalize().resolve("logs");
        this.dailyLimitBytes = dailyLimitBytes;
    }

    public boolean recordBlocked(Action action, Evaluation evaluation) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("at", evaluation.evaluatedAt().toString());
        event.put("capability", action.capability().name().toLowerCase());
        event.put("operation", sanitize(action.operation(), 80));
        event.put("resource", truncate(evaluation.sanitizedResource(), MAX_RESOURCE_CHARS));
        event.put("risk", evaluation.risk().name().toLowerCase());
        String reason = evaluation.findings().isEmpty() ? evaluation.reason() : evaluation.findings().getFirst();
        event.put("reason", sanitize(reason, MAX_MESSAGE_CHARS));
        return append("blocked", event);
    }

    @Override
    public boolean recordDecision(Action action, Evaluation evaluation) {
        return evaluation.decision() != Decision.DENY || recordBlocked(action, evaluation);
    }

    @Override
    public boolean recordError(String component, String message) {
        SecretScanner.ScanResult sanitized = secrets.scan(message == null ? "unknown error" : message);
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("at", java.time.Instant.now().toString());
        event.put("component", truncate(component, 64));
        event.put("message", truncate(sanitized.redacted(), MAX_MESSAGE_CHARS));
        return append("error", event);
    }

    @Override
    public boolean recordControl(String type, Map<String, Object> details) {
        // Local-agent mode deliberately does not persist control or approval events.
        return true;
    }

    public List<Map<String, Object>> recentBlocked(int requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit, 100));
        Path path = path("blocked", LocalDate.now());
        if (!Files.isRegularFile(path)) return List.of();
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            List<Map<String, Object>> result = new ArrayList<>();
            for (int index = lines.size() - 1; index >= 0 && result.size() < limit; index--) {
                try {
                    result.add(Json.object(lines.get(index)));
                } catch (IllegalArgumentException ignored) {
                    // A partial final line after a host crash is ignored, never amplified.
                }
            }
            return List.copyOf(result);
        } catch (IOException error) {
            reportWriteFailure("read blocked events", error);
            return List.of();
        }
    }

    public DailyStats todayStats() {
        LocalDate today = LocalDate.now();
        Path blocked = path("blocked", today);
        Path errors = path("error", today);
        long blockedBytes = size(blocked);
        long errorBytes = size(errors);
        return new DailyStats(countLines(blocked), countLines(errors), blockedBytes + errorBytes, dailyLimitBytes);
    }

    public Path blockedPath() {
        return path("blocked", LocalDate.now());
    }

    public Path errorPath() {
        return path("error", LocalDate.now());
    }

    private boolean append(String category, Map<String, Object> event) {
        byte[] bytes = (Json.stringify(event) + "\n").getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_EVENT_BYTES) return false;
        try {
            synchronized (JVM_WRITE_LOCK) {
                Files.createDirectories(logDirectory);
                Path lockPath = logDirectory.resolve(".write.lock");
                LocalDate today = LocalDate.now();
                try (FileChannel lockChannel = FileChannel.open(lockPath,
                        StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                     FileLock lock = lockChannel.lock()) {
                    if (!lock.isValid()) return false;
                    long used = size(path("blocked", today)) + size(path("error", today));
                    if (used + bytes.length > dailyLimitBytes) return false;
                    try (FileChannel output = FileChannel.open(path(category, today), StandardOpenOption.CREATE,
                            StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
                        ByteBuffer buffer = ByteBuffer.wrap(bytes);
                        while (buffer.hasRemaining()) output.write(buffer);
                    }
                    return true;
                }
            }
        } catch (IOException error) {
            reportWriteFailure("write security event", error);
            return false;
        }
    }

    private Path path(String category, LocalDate day) {
        return logDirectory.resolve(category + "-" + day + ".jsonl");
    }

    private long size(Path path) {
        try {
            return Files.isRegularFile(path) ? Files.size(path) : 0L;
        } catch (IOException ignored) {
            return 0L;
        }
    }

    private long countLines(Path path) {
        if (!Files.isRegularFile(path)) return 0L;
        try (var lines = Files.lines(path, StandardCharsets.UTF_8)) {
            return lines.count();
        } catch (IOException ignored) {
            return 0L;
        }
    }

    private void reportWriteFailure(String operation, IOException error) {
        if (writeFailureReported.compareAndSet(false, true)) {
            System.err.println("CarbonGate log error: " + operation + " failed: " + error.getMessage());
        }
    }

    private String truncate(String value, int limit) {
        if (value == null) return "";
        return value.length() <= limit ? value : value.substring(0, limit) + "…";
    }

    private String sanitize(String value, int limit) {
        return truncate(secrets.scan(value == null ? "" : value).redacted(), limit);
    }

    public record DailyStats(long blockedEvents, long internalErrors, long bytesWritten, long byteLimit) {}
}
