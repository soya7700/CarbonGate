package io.carbongate.provider.audit;

import io.carbongate.json.Json;

import java.io.IOException;
import java.io.BufferedReader;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bounded append-only audit sink with a daily SHA-256 forward hash chain. */
public final class EnterpriseAuditProvider {
    private static final String API_VERSION = "carbongate.provider/v1";
    private static final long DAILY_LIMIT = 100_000_000L;
    private static final int MAX_LAST_RECORD = 65_536;
    private static final String GENESIS = "0".repeat(64);

    private EnterpriseAuditProvider() {}

    public static void main(String[] args) throws Exception {
        Map<String, Object> request = Json.object(new String(System.in.readAllBytes(), StandardCharsets.UTF_8).trim());
        String id = text(request.get("id"), "request id", 128);
        String operation = text(request.get("operation"), "operation", 32);
        Path component = Path.of(".").toAbsolutePath().normalize();
        Map<String, Object> result = switch (operation) {
            case "health" -> health(component);
            case "audit" -> append(object(request.get("payload"), "payload"), component);
            default -> throw new IllegalArgumentException("Unsupported operation");
        };
        System.out.println(Json.stringify(Map.of("apiVersion", API_VERSION, "id", id,
                "status", "ok", "result", result)));
    }

    static Map<String, Object> append(Map<String, Object> payload, Path component) throws IOException {
        Map<String, Object> event = sanitize(payload);
        Path state = component.resolve("state");
        Files.createDirectories(state);
        Path log = state.resolve("audit-" + LocalDate.now(ZoneOffset.UTC) + ".jsonl");
        try (FileChannel channel = FileChannel.open(log, StandardOpenOption.CREATE, StandardOpenOption.READ,
                StandardOpenOption.WRITE); FileLock lock = channel.lock()) {
            if (!lock.isValid()) throw new IOException("Enterprise audit lock is invalid");
            Last last = last(channel);
            if (!last.valid()) throw new IOException("Enterprise audit hash chain tail is invalid");
            long sequence = last.sequence() + 1;
            Map<String, Object> chained = new LinkedHashMap<>();
            chained.put("sequence", sequence);
            chained.put("timestamp", Instant.now().toString());
            chained.put("previousHash", last.hash());
            chained.put("event", event);
            String hash = sha256(last.hash() + "\n" + Json.stringify(chained));
            chained.put("hash", hash);
            byte[] line = (Json.stringify(chained) + "\n").getBytes(StandardCharsets.UTF_8);
            if (channel.size() + line.length > DAILY_LIMIT) throw new IOException("Enterprise audit daily limit reached");
            channel.position(channel.size());
            ByteBuffer buffer = ByteBuffer.wrap(line);
            while (buffer.hasRemaining()) channel.write(buffer);
            channel.force(true);
            return Map.of("stored", true, "sequence", sequence, "hash", hash,
                    "file", log.getFileName().toString(), "contentStored", false);
        }
    }

    static Map<String, Object> health(Path component) throws IOException {
        Path state = component.resolve("state");
        Files.createDirectories(state);
        Path log = state.resolve("audit-" + LocalDate.now(ZoneOffset.UTC) + ".jsonl");
        if (!Files.exists(log) || Files.size(log) == 0) {
            return Map.of("health", "pass", "chain", "empty", "dailyLimitBytes", DAILY_LIMIT);
        }
        Last last = verifyChain(log);
        return Map.of("health", "pass", "chain", "valid", "sequence", last.sequence(),
                "dailyLimitBytes", DAILY_LIMIT);
    }

    private static Last verifyChain(Path log) throws IOException {
        long expectedSequence = 1;
        String expectedPrevious = GENESIS;
        try (BufferedReader reader = Files.newBufferedReader(log, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.length() > MAX_LAST_RECORD) throw new IOException("Enterprise audit record exceeds 64 KiB");
                Map<String, Object> record = Json.object(line);
                Object rawSequence = record.get("sequence");
                String previous = String.valueOf(record.get("previousHash"));
                String hash = String.valueOf(record.remove("hash"));
                if (!(rawSequence instanceof Number sequence) || sequence.longValue() != expectedSequence
                        || !previous.equals(expectedPrevious) || !hash.matches("[0-9a-f]{64}")
                        || !hash.equals(sha256(previous + "\n" + Json.stringify(record)))) {
                    throw new IOException("Enterprise audit hash chain is invalid");
                }
                expectedPrevious = hash;
                expectedSequence++;
            }
        }
        return new Last(expectedSequence - 1, expectedPrevious, true);
    }

    private static Map<String, Object> sanitize(Map<String, Object> payload) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", text(payload.get("eventId"), "eventId", 128));
        event.put("action", text(payload.get("action"), "action", 128));
        event.put("risk", choice(payload.get("risk"), "risk", List.of("low", "medium", "high", "critical")));
        event.put("decision", choice(payload.get("decision"), "decision", List.of("allow", "ask", "deny")));
        Object raw = payload.get("components");
        if (!(raw instanceof List<?> list) || list.size() > 64) {
            throw new IllegalArgumentException("components must be an array of at most 64 values");
        }
        List<String> components = new ArrayList<>();
        for (Object value : list) components.add(text(value, "component", 160));
        event.put("components", List.copyOf(components));
        return Map.copyOf(event);
    }

    private static Last last(FileChannel channel) throws IOException {
        long size = channel.size();
        if (size == 0) return new Last(0, GENESIS, true);
        int length = (int) Math.min(size, MAX_LAST_RECORD);
        ByteBuffer buffer = ByteBuffer.allocate(length);
        channel.read(buffer, size - length);
        String tail = new String(buffer.array(), StandardCharsets.UTF_8);
        int end = tail.endsWith("\n") ? tail.length() - 1 : tail.length();
        int start = tail.lastIndexOf('\n', Math.max(0, end - 1)) + 1;
        if (start == 0 && size > MAX_LAST_RECORD) throw new IOException("Enterprise audit record exceeds 64 KiB");
        Map<String, Object> record = Json.object(tail.substring(start, end));
        Object rawSequence = record.get("sequence");
        String previous = String.valueOf(record.get("previousHash"));
        String hash = String.valueOf(record.remove("hash"));
        if (!(rawSequence instanceof Number sequence) || !previous.matches("[0-9a-f]{64}")
                || !hash.matches("[0-9a-f]{64}")) return new Last(0, "", false);
        String expected = sha256(previous + "\n" + Json.stringify(record));
        return new Last(sequence.longValue(), hash, hash.equals(expected));
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static String choice(Object value, String name, List<String> allowed) {
        String text = text(value, name, 16);
        if (!allowed.contains(text)) throw new IllegalArgumentException(name + " is invalid");
        return text;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value, String name) {
        if (value instanceof Map<?, ?> map) return (Map<String, Object>) map;
        throw new IllegalArgumentException(name + " must be an object");
    }

    private static String text(Object value, String name, int limit) {
        if (value instanceof String text && !text.isBlank() && text.length() <= limit
                && text.indexOf('\n') < 0 && text.indexOf('\r') < 0) return text;
        throw new IllegalArgumentException(name + " is required, invalid, or too long");
    }

    private record Last(long sequence, String hash, boolean valid) {}
}
