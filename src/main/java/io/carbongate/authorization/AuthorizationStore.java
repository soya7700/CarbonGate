package io.carbongate.authorization;

import io.carbongate.json.Json;
import io.carbongate.model.Action;
import io.carbongate.model.Evaluation;
import io.carbongate.security.SecretScanner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public final class AuthorizationStore {
    private static final int MAX_PENDING = 100;
    private static final Duration EXPIRY = Duration.ofHours(24);
    private final Path pendingDirectory;
    private final Path approvedDirectory;
    private final SecretScanner secrets = new SecretScanner();

    public AuthorizationStore(Path home) {
        Path approvals = home.toAbsolutePath().normalize().resolve("approvals");
        this.pendingDirectory = approvals.resolve("pending");
        this.approvedDirectory = approvals.resolve("approved");
    }

    public synchronized String ensurePending(Action action, Evaluation evaluation) throws IOException {
        prepare();
        String fingerprint = fingerprint(action);
        for (Path file : files(pendingDirectory)) {
            Map<String, Object> entry = read(file);
            if (fingerprint.equals(String.valueOf(entry.get("fingerprint")))) {
                return String.valueOf(entry.get("id"));
            }
        }
        trimPending();
        Map<String, Object> entry = entry(action, evaluation, fingerprint);
        write(pendingDirectory.resolve(evaluation.id() + ".json"), entry);
        return evaluation.id();
    }

    public synchronized List<Map<String, Object>> pending() throws IOException {
        if (!Files.isDirectory(pendingDirectory)) return List.of();
        cleanupExpired(pendingDirectory);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Path file : files(pendingDirectory)) result.add(read(file));
        result.sort(Comparator.comparing(value -> String.valueOf(value.get("createdAt"))));
        return List.copyOf(result);
    }

    public synchronized boolean approve(String id) throws IOException {
        validateId(id);
        prepare();
        Path source = pendingDirectory.resolve(id + ".json");
        if (!Files.isRegularFile(source)) return false;
        Files.move(source, approvedDirectory.resolve(id + ".json"), StandardCopyOption.REPLACE_EXISTING);
        return true;
    }

    public synchronized boolean deny(String id) throws IOException {
        validateId(id);
        return Files.deleteIfExists(pendingDirectory.resolve(id + ".json")) |
                Files.deleteIfExists(approvedDirectory.resolve(id + ".json"));
    }

    public synchronized boolean consumeApproved(Action action) throws IOException {
        prepare();
        String fingerprint = fingerprint(action);
        for (Path file : files(approvedDirectory)) {
            Map<String, Object> entry = read(file);
            if (fingerprint.equals(String.valueOf(entry.get("fingerprint")))) {
                Files.deleteIfExists(file);
                return true;
            }
        }
        return false;
    }

    public int pendingCount() {
        try {
            return pending().size();
        } catch (IOException | RuntimeException ignored) {
            return 0;
        }
    }

    private void prepare() throws IOException {
        Files.createDirectories(pendingDirectory);
        Files.createDirectories(approvedDirectory);
        cleanupExpired(pendingDirectory);
        cleanupExpired(approvedDirectory);
    }

    private void cleanupExpired(Path directory) throws IOException {
        Instant cutoff = Instant.now().minus(EXPIRY);
        for (Path file : files(directory)) {
            if (Files.getLastModifiedTime(file).toInstant().isBefore(cutoff)) Files.deleteIfExists(file);
        }
    }

    private void trimPending() throws IOException {
        List<Path> pending = files(pendingDirectory);
        while (pending.size() >= MAX_PENDING) {
            Files.deleteIfExists(pending.removeFirst());
        }
    }

    private List<Path> files(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) return new ArrayList<>();
        try (Stream<Path> stream = Files.list(directory)) {
            return stream.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparingLong(this::modifiedTime))
                    .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        }
    }

    private long modifiedTime(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException ignored) {
            return Long.MAX_VALUE;
        }
    }

    private Map<String, Object> entry(Action action, Evaluation evaluation, String fingerprint) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("id", evaluation.id());
        entry.put("createdAt", Instant.now().toString());
        entry.put("fingerprint", fingerprint);
        entry.put("actor", sanitize(action.actor(), 128));
        entry.put("capability", action.capability().name().toLowerCase());
        entry.put("operation", sanitize(action.operation(), 128));
        entry.put("resource", truncate(evaluation.sanitizedResource(), 1024));
        entry.put("workspace", sanitize(action.workspace().toString(), 1024));
        entry.put("risk", evaluation.risk().name().toLowerCase());
        return entry;
    }

    private void write(Path target, Map<String, Object> entry) throws IOException {
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(temporary, Json.stringify(entry), StandardCharsets.UTF_8);
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private Map<String, Object> read(Path file) throws IOException {
        return Json.object(Files.readString(file, StandardCharsets.UTF_8));
    }

    private String fingerprint(Action action) {
        String value = action.capability() + "\n" + action.operation() + "\n" + action.resource() + "\n" +
                action.workspace();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private void validateId(String id) {
        if (id == null || !id.matches("[0-9a-fA-F-]{36}")) {
            throw new IllegalArgumentException("Invalid approval id");
        }
    }

    private String truncate(String value, int limit) {
        if (value == null) return "";
        return value.length() <= limit ? value : value.substring(0, limit) + "…";
    }

    private String sanitize(String value, int limit) {
        return truncate(secrets.scan(value == null ? "" : value).redacted(), limit);
    }
}
