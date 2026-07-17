package io.carbongate.enterprise.component;

import io.carbongate.json.Json;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class ComponentStore {
    private static final long MAX_ARCHIVE_BYTES = 50_000_000L;
    private static final long MAX_ENTRY_BYTES = 20_000_000L;
    private static final int MAX_ENTRIES = 512;
    private final Path root;
    private final Path registry;

    public ComponentStore(Path carbonHome) {
        root = carbonHome.toAbsolutePath().normalize().resolve("enterprise").resolve("components");
        registry = root.resolve("registry.json");
    }

    public synchronized ComponentManifest install(Path archive) throws IOException {
        Path source = archive.toAbsolutePath().normalize();
        if (!source.getFileName().toString().endsWith(".carbon") || !Files.isRegularFile(source)) {
            throw new IllegalArgumentException("Component package must be an existing .carbon file");
        }
        if (Files.size(source) > MAX_ARCHIVE_BYTES) throw new IllegalArgumentException("Component package exceeds 50 MB");
        Files.createDirectories(root);
        Path staging = root.resolve(".staging-" + UUID.randomUUID()).normalize();
        Files.createDirectory(staging);
        try {
            extract(source, staging);
            requireFile(staging.resolve("LICENSE"));
            requireFile(staging.resolve("NOTICE"));
            requireFile(staging.resolve("checksums.json"));
            ComponentManifest manifest = ComponentManifest.read(staging.resolve("manifest.json"));
            verifyChecksums(staging);
            Path target = componentDirectory(manifest.id(), manifest.version());
            if (Files.exists(target)) throw new IllegalArgumentException("Component version is already installed");
            Files.createDirectories(target.getParent());
            move(staging, target);
            return manifest;
        } catch (IOException | RuntimeException error) {
            deleteTree(staging);
            throw error;
        }
    }

    public synchronized List<Map<String, Object>> list() throws IOException {
        Map<String, String> active = activeVersions();
        if (!Files.isDirectory(root)) return List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        try (var ids = Files.list(root)) {
            for (Path idDirectory : ids.filter(Files::isDirectory)
                    .filter(path -> !path.getFileName().toString().startsWith(".")).toList()) {
                try (var versions = Files.list(idDirectory)) {
                    for (Path versionDirectory : versions.filter(Files::isDirectory).toList()) {
                        ComponentManifest manifest = ComponentManifest.read(versionDirectory.resolve("manifest.json"));
                        Map<String, Object> value = new LinkedHashMap<>(manifest.map());
                        value.put("enabled", manifest.version().equals(active.get(manifest.id())));
                        value.put("directory", versionDirectory.toString());
                        result.add(value);
                    }
                }
            }
        }
        result.sort(Comparator.comparing(value -> value.get("id") + ":" + value.get("version")));
        return List.copyOf(result);
    }

    public ComponentManifest require(String id, String version) throws IOException {
        Path directory = componentDirectory(id, version);
        if (!Files.isDirectory(directory)) throw new IllegalArgumentException("Component version is not installed: " + id + "@" + version);
        return ComponentManifest.read(directory.resolve("manifest.json"));
    }

    public ComponentManifest requireActive(String id) throws IOException {
        String version = activeVersions().get(id);
        if (version == null) throw new IllegalArgumentException("Component is not enabled: " + id);
        return require(id, version);
    }

    public synchronized void activate(String id, String version) throws IOException {
        require(id, version);
        Map<String, String> active = new LinkedHashMap<>(activeVersions());
        active.put(id, version);
        writeRegistry(active);
    }

    public synchronized boolean disable(String id) throws IOException {
        Map<String, String> active = new LinkedHashMap<>(activeVersions());
        if (active.remove(id) == null) return false;
        writeRegistry(active);
        return true;
    }

    public synchronized void remove(String id, String version) throws IOException {
        if (version.equals(activeVersions().get(id))) {
            throw new IllegalArgumentException("Disable the active component before removing it");
        }
        Path target = componentDirectory(id, version);
        if (!Files.isDirectory(target)) throw new IllegalArgumentException("Component version is not installed: " + id + "@" + version);
        deleteTree(target);
        Path parent = target.getParent();
        boolean empty;
        try (var children = Files.list(parent)) {
            empty = children.findAny().isEmpty();
        }
        if (empty) Files.deleteIfExists(parent);
    }

    public Path componentDirectory(String id, String version) {
        if (id == null || !id.matches("[a-z][a-z0-9.-]{2,63}") || version == null
                || !version.matches("[0-9]+\\.[0-9]+\\.[0-9]+(?:[-+][A-Za-z0-9.-]+)?")) {
            throw new IllegalArgumentException("Invalid component id or version");
        }
        return root.resolve(id).resolve(version).normalize();
    }

    public Path root() {
        return root;
    }

    private Map<String, String> activeVersions() throws IOException {
        if (!Files.isRegularFile(registry)) return Map.of();
        Map<String, Object> value = Json.object(Files.readString(registry, StandardCharsets.UTF_8));
        if (!(value.get("version") instanceof Number registryVersion) || registryVersion.intValue() != 1) {
            throw new IllegalArgumentException("Unsupported component registry version");
        }
        Object raw = value.get("active");
        if (!(raw instanceof Map<?, ?> map)) return Map.of();
        Map<String, String> result = new LinkedHashMap<>();
        map.forEach((key, version) -> result.put(String.valueOf(key), String.valueOf(version)));
        return Map.copyOf(result);
    }

    private void writeRegistry(Map<String, String> active) throws IOException {
        Files.createDirectories(root);
        Path temporary = registry.resolveSibling("registry.json.tmp");
        Files.writeString(temporary, Json.stringify(Map.of("version", 1, "active", active)), StandardCharsets.UTF_8);
        move(temporary, registry);
    }

    private void extract(Path archive, Path staging) throws IOException {
        int entries = 0;
        long total = 0;
        Set<String> extracted = new HashSet<>();
        try (ZipInputStream input = new ZipInputStream(Files.newInputStream(archive), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                if (++entries > MAX_ENTRIES) throw new IllegalArgumentException("Component package has too many entries");
                String name = entry.getName();
                if (name.isBlank() || name.startsWith("/") || name.contains("\\") || name.contains("\0")) {
                    throw new IllegalArgumentException("Component package contains an unsafe path");
                }
                for (String segment : name.split("/")) {
                    if (segment.equals(".") || segment.equals("..")) {
                        throw new IllegalArgumentException("Component package path escapes staging");
                    }
                }
                Path target = staging.resolve(name).normalize();
                if (!target.startsWith(staging)) throw new IllegalArgumentException("Component package path escapes staging");
                String normalized = staging.relativize(target).toString();
                if (!extracted.add(normalized)) {
                    throw new IllegalArgumentException("Component package contains a duplicate path");
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    long written = copyBounded(input, target);
                    total += written;
                    if (total > MAX_ARCHIVE_BYTES) throw new IllegalArgumentException("Expanded component exceeds 50 MB");
                }
                input.closeEntry();
            }
        }
    }

    private long copyBounded(InputStream input, Path target) throws IOException {
        byte[] buffer = new byte[8192];
        long written = 0;
        try (var output = Files.newOutputStream(target)) {
            int count;
            while ((count = input.read(buffer)) >= 0) {
                written += count;
                if (written > MAX_ENTRY_BYTES) throw new IllegalArgumentException("Component entry exceeds 20 MB");
                output.write(buffer, 0, count);
            }
        }
        return written;
    }

    private void verifyChecksums(Path staging) throws IOException {
        Map<String, Object> rootValue = Json.object(Files.readString(staging.resolve("checksums.json"), StandardCharsets.UTF_8));
        if (!"SHA-256".equals(rootValue.get("algorithm"))) throw new IllegalArgumentException("Component checksums must use SHA-256");
        Object rawFiles = rootValue.get("files");
        if (!(rawFiles instanceof Map<?, ?> files)) throw new IllegalArgumentException("Component checksums require files");
        List<Path> payloadFiles;
        Path payload = staging.resolve("payload");
        if (Files.isDirectory(payload)) {
            try (var paths = Files.walk(payload)) {
                payloadFiles = paths.filter(Files::isRegularFile).toList();
            }
        } else {
            payloadFiles = List.of();
        }
        if (files.size() != payloadFiles.size()) throw new IllegalArgumentException("Every payload file requires exactly one checksum");
        for (Path file : payloadFiles) {
            String relative = staging.relativize(file).toString().replace(java.io.File.separatorChar, '/');
            Object expected = files.get(relative);
            if (!(expected instanceof String hash) || !hash.matches("[0-9a-f]{64}") || !hash.equals(sha256(file))) {
                throw new IllegalArgumentException("Component checksum mismatch: " + relative);
            }
        }
    }

    private String sha256(Path file) throws IOException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private void requireFile(Path file) {
        if (!Files.isRegularFile(file)) throw new IllegalArgumentException("Component package is missing " + file.getFileName());
    }

    private void move(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void deleteTree(Path path) throws IOException {
        if (!Files.exists(path)) return;
        try (var paths = Files.walk(path)) {
            for (Path item : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(item);
        }
    }
}
