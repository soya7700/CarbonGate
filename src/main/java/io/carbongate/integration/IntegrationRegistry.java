package io.carbongate.integration;

import io.carbongate.json.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class IntegrationRegistry {
    private final Path path;

    public IntegrationRegistry(Path carbonHome) {
        path = carbonHome.toAbsolutePath().normalize().resolve("integrations").resolve("registry.json");
    }

    public synchronized Map<String, Entry> entries() throws IOException {
        if (!Files.isRegularFile(path)) return Map.of();
        Map<String, Object> root = Json.object(Files.readString(path, StandardCharsets.UTF_8));
        Object rawEntries = root.get("entries");
        if (!(rawEntries instanceof List<?> list)) return Map.of();
        Map<String, Entry> result = new LinkedHashMap<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> raw)) continue;
            Entry entry = Entry.from(raw);
            result.put(entry.host(), entry);
        }
        return Map.copyOf(result);
    }

    public synchronized void put(Entry entry) throws IOException {
        Map<String, Entry> updated = new LinkedHashMap<>(entries());
        updated.put(entry.host(), entry);
        write(updated);
    }

    public synchronized boolean remove(String host) throws IOException {
        Map<String, Entry> updated = new LinkedHashMap<>(entries());
        if (updated.remove(host) == null) return false;
        write(updated);
        return true;
    }

    public Path path() {
        return path;
    }

    private void write(Map<String, Entry> entries) throws IOException {
        Files.createDirectories(path.getParent());
        List<Map<String, Object>> values = entries.values().stream().map(Entry::map).toList();
        String content = Json.stringify(Map.of("version", 1, "entries", values));
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(temporary, content, StandardCharsets.UTF_8);
        try {
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public record Entry(String host, String coverage, List<String> command, String installedAt) {
        public static Entry installed(HostDefinition host, List<String> command) {
            return new Entry(host.id(), host.coverage().name(), List.copyOf(command), Instant.now().toString());
        }

        private Map<String, Object> map() {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("host", host);
            value.put("coverage", coverage);
            value.put("command", command);
            value.put("installedAt", installedAt);
            return value;
        }

        private static Entry from(Map<?, ?> value) {
            Object command = value.get("command");
            List<String> arguments = new ArrayList<>();
            if (command instanceof List<?> list) list.forEach(item -> arguments.add(String.valueOf(item)));
            return new Entry(String.valueOf(value.get("host")), String.valueOf(value.get("coverage")),
                    List.copyOf(arguments), String.valueOf(value.get("installedAt")));
        }
    }
}
