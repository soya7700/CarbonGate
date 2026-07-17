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

public final class ProtectedRouteRegistry {
    private final Path path;

    public ProtectedRouteRegistry(Path home) {
        path = home.toAbsolutePath().normalize().resolve("protections").resolve("registry.json");
    }

    public synchronized Map<String, Entry> entries() throws IOException {
        if (!Files.isRegularFile(path)) return Map.of();
        Map<String, Object> root = Json.object(Files.readString(path, StandardCharsets.UTF_8));
        Object rawEntries = root.get("entries");
        if (!(rawEntries instanceof List<?> values)) return Map.of();
        Map<String, Entry> result = new LinkedHashMap<>();
        for (Object value : values) {
            if (!(value instanceof Map<?, ?> map)) continue;
            Entry entry = Entry.from(map);
            result.put(entry.key(), entry);
        }
        return Map.copyOf(result);
    }

    public synchronized void put(Entry entry) throws IOException {
        Map<String, Entry> updated = new LinkedHashMap<>(entries());
        updated.put(entry.key(), entry);
        write(updated.values().stream().toList());
    }

    public synchronized boolean remove(String host, String name) throws IOException {
        Map<String, Entry> updated = new LinkedHashMap<>(entries());
        if (updated.remove(Entry.key(host, name)) == null) return false;
        write(updated.values().stream().toList());
        return true;
    }

    public Path path() {
        return path;
    }

    private void write(List<Entry> entries) throws IOException {
        Files.createDirectories(path.getParent());
        String content = Json.stringify(Map.of("version", 1,
                "entries", entries.stream().map(Entry::map).toList()));
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(temporary, content, StandardCharsets.UTF_8);
        try {
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public record Entry(String host, String name, String serverName, String workspace, String installedAt) {
        public static Entry create(String host, String name, String serverName, Path workspace) {
            return new Entry(host, name, serverName, workspace.toAbsolutePath().normalize().toString(),
                    Instant.now().toString());
        }

        public String key() {
            return key(host, name);
        }

        private static String key(String host, String name) {
            return host + ":" + name;
        }

        public Map<String, Object> map() {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("host", host);
            value.put("name", name);
            value.put("serverName", serverName);
            value.put("workspace", workspace);
            value.put("coverage", "mcp_only");
            value.put("installedAt", installedAt);
            return value;
        }

        private static Entry from(Map<?, ?> value) {
            return new Entry(String.valueOf(value.get("host")), String.valueOf(value.get("name")),
                    String.valueOf(value.get("serverName")), String.valueOf(value.get("workspace")),
                    String.valueOf(value.get("installedAt")));
        }
    }
}
