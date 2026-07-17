package io.carbongate.mcp;

import io.carbongate.json.Json;
import io.carbongate.security.SecretScanner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Compact, atomic registry for protected upstream MCP stdio routes. */
public final class McpProfileStore {
    private static final int MAX_PROFILES = 100;
    private static final int MAX_ARGUMENTS = 64;
    private static final int MAX_ARGUMENT_LENGTH = 4096;
    private static final int MAX_COMMAND_BYTES = 32 * 1024;
    private static final int MAX_REGISTRY_BYTES = 1024 * 1024;
    private static final Pattern NAME = Pattern.compile("[a-z0-9][a-z0-9._-]{0,63}");
    private static final Set<String> CREDENTIAL_OPTIONS = Set.of(
            "--token", "--api-key", "--apikey", "--password", "--passwd", "--secret",
            "--authorization", "--auth-token", "--access-token", "--client-secret");

    private final Path path;
    private final SecretScanner secrets = new SecretScanner();

    public McpProfileStore(Path carbonHome) {
        path = carbonHome.toAbsolutePath().normalize().resolve("mcp").resolve("profiles.json");
    }

    public synchronized List<Profile> list() throws IOException {
        if (!Files.isRegularFile(path)) return List.of();
        if (Files.size(path) > MAX_REGISTRY_BYTES) {
            throw new IOException("MCP profile registry exceeds the 1 MiB safety limit");
        }
        Map<String, Object> root = Json.object(Files.readString(path, StandardCharsets.UTF_8));
        Object profiles = root.get("profiles");
        if (!(profiles instanceof List<?> values)) return List.of();
        if (values.size() > MAX_PROFILES) throw new IOException("MCP profile registry exceeds the profile limit");
        List<Profile> result = new ArrayList<>();
        for (Object value : values) {
            if (!(value instanceof Map<?, ?> map)) throw new IOException("MCP profile registry contains an invalid entry");
            Profile parsed = Profile.from(map);
            Profile validated = new Profile(normalizeName(parsed.name()), normalizeStoredWorkspace(parsed.workspace()),
                    validateCommand(parsed.command()), parsed.createdAt(), parsed.updatedAt());
            if (result.stream().anyMatch(profile -> profile.name().equals(validated.name()))) {
                throw new IOException("MCP profile registry contains duplicate names");
            }
            result.add(validated);
        }
        result.sort(Comparator.comparing(Profile::name));
        return List.copyOf(result);
    }

    public synchronized Profile require(String requestedName) throws IOException {
        String name = normalizeName(requestedName);
        Profile profile = list().stream().filter(value -> value.name().equals(name)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown MCP profile: " + name));
        return new Profile(profile.name(), validateWorkspace(profile.workspace()), profile.command(),
                profile.createdAt(), profile.updatedAt());
    }

    public synchronized Profile put(String requestedName, Path workspace, List<String> command,
                                    boolean replace) throws IOException {
        String name = normalizeName(requestedName);
        Path root = validateWorkspace(workspace);
        List<String> safeCommand = validateCommand(command);
        Map<String, Profile> profiles = new LinkedHashMap<>();
        list().forEach(profile -> profiles.put(profile.name(), profile));
        Profile existing = profiles.get(name);
        if (existing != null && !replace) {
            throw new IllegalArgumentException("MCP profile already exists: " + name + "; use --replace to update it");
        }
        if (existing == null && profiles.size() >= MAX_PROFILES) {
            throw new IllegalArgumentException("MCP profile limit reached (" + MAX_PROFILES + ")");
        }
        String now = Instant.now().toString();
        Profile profile = new Profile(name, root, safeCommand,
                existing == null ? now : existing.createdAt(), now);
        profiles.put(name, profile);
        write(profiles.values().stream().sorted(Comparator.comparing(Profile::name)).toList());
        return profile;
    }

    public synchronized boolean remove(String requestedName) throws IOException {
        String name = normalizeName(requestedName);
        List<Profile> profiles = new ArrayList<>(list());
        boolean changed = profiles.removeIf(profile -> profile.name().equals(name));
        if (changed) write(profiles);
        return changed;
    }

    public Path path() {
        return path;
    }

    private void write(List<Profile> profiles) throws IOException {
        Files.createDirectories(path.getParent());
        String content = Json.stringify(Map.of("version", 1,
                "profiles", profiles.stream().map(Profile::map).toList()));
        if (content.getBytes(StandardCharsets.UTF_8).length > MAX_REGISTRY_BYTES) {
            throw new IOException("MCP profile registry exceeds the 1 MiB safety limit");
        }
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(temporary, content, StandardCharsets.UTF_8);
        ownerOnly(temporary);
        try {
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        }
        ownerOnly(path);
    }

    private void ownerOnly(Path file) throws IOException {
        try {
            Files.setPosixFilePermissions(file, Set.of(PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException ignored) {
            // Windows and non-POSIX file systems use their native ACL inheritance.
        }
    }

    private String normalizeName(String value) {
        String name = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("MCP profile name must match " + NAME.pattern());
        }
        return name;
    }

    private Path validateWorkspace(Path value) {
        if (value == null) throw new IllegalArgumentException("MCP profile workspace is required");
        Path workspace = value.toAbsolutePath().normalize();
        if (!Files.isDirectory(workspace)) {
            throw new IllegalArgumentException("MCP profile workspace must be an existing directory");
        }
        return workspace;
    }

    private Path normalizeStoredWorkspace(Path value) {
        if (value == null || !value.isAbsolute()) {
            throw new IllegalArgumentException("MCP profile workspace must be absolute");
        }
        return value.normalize();
    }

    private List<String> validateCommand(List<String> values) {
        if (values == null || values.isEmpty()) throw new IllegalArgumentException("MCP server command is required after --");
        if (values.size() > MAX_ARGUMENTS) throw new IllegalArgumentException("MCP server command has too many arguments");
        if (secrets.scan(String.join(" ", values)).containsSecrets()) {
            throw new IllegalArgumentException("MCP profile rejected secret-like command content; pass secrets through the process environment");
        }
        int bytes = 0;
        for (int index = 0; index < values.size(); index++) {
            String value = values.get(index);
            if (value == null || value.isBlank() || value.length() > MAX_ARGUMENT_LENGTH
                    || value.indexOf('\0') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
                throw new IllegalArgumentException("MCP server command contains an invalid argument");
            }
            bytes += value.getBytes(StandardCharsets.UTF_8).length;
            String option = value.toLowerCase(Locale.ROOT);
            int equals = option.indexOf('=');
            String optionName = equals < 0 ? option : option.substring(0, equals);
            if (CREDENTIAL_OPTIONS.contains(optionName)) {
                throw new IllegalArgumentException("MCP profile rejected a credential option; pass secrets through the process environment");
            }
        }
        if (bytes > MAX_COMMAND_BYTES) throw new IllegalArgumentException("MCP server command exceeds 32 KiB");
        return List.copyOf(values);
    }

    public record Profile(String name, Path workspace, List<String> command,
                          String createdAt, String updatedAt) {
        public Profile {
            command = List.copyOf(command);
        }

        public Map<String, Object> map() {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("name", name);
            value.put("workspace", workspace.toString());
            value.put("command", command);
            value.put("createdAt", createdAt);
            value.put("updatedAt", updatedAt);
            return value;
        }

        private static Profile from(Map<?, ?> value) {
            List<String> command = new ArrayList<>();
            Object rawCommand = value.get("command");
            if (rawCommand instanceof List<?> values) values.forEach(item -> command.add(String.valueOf(item)));
            return new Profile(String.valueOf(value.get("name")), Path.of(String.valueOf(value.get("workspace"))),
                    command, String.valueOf(value.get("createdAt")), String.valueOf(value.get("updatedAt")));
        }
    }
}
