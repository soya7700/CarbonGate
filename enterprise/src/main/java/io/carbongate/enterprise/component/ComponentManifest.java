package io.carbongate.enterprise.component;

import io.carbongate.json.Json;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public record ComponentManifest(String apiVersion, Kind kind, String id, String version,
                                List<String> entrypoint, Set<String> operations,
                                Set<String> permissions, int timeoutMillis,
                                FailureMode failureMode, String spdxLicense) {
    public static final String API_VERSION = "carbongate.io/v1";
    private static final Set<String> ALLOWED_OPERATIONS = Set.of("inspect", "authorize", "audit", "sandbox");

    public ComponentManifest {
        if (!API_VERSION.equals(apiVersion)) throw new IllegalArgumentException("Unsupported component apiVersion");
        if (kind == null) throw new IllegalArgumentException("Component kind is required");
        if (id == null || !id.matches("[a-z][a-z0-9.-]{2,63}")) {
            throw new IllegalArgumentException("Component id must match [a-z][a-z0-9.-]{2,63}");
        }
        if (version == null || !version.matches("[0-9]+\\.[0-9]+\\.[0-9]+(?:[-+][A-Za-z0-9.-]+)?")) {
            throw new IllegalArgumentException("Component version must use semantic version syntax");
        }
        entrypoint = entrypoint == null ? List.of() : List.copyOf(entrypoint);
        operations = operations == null ? Set.of() : Set.copyOf(operations);
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
        if (!ALLOWED_OPERATIONS.containsAll(operations)) throw new IllegalArgumentException("Unknown component operation");
        if (entrypoint.size() > 32 || entrypoint.stream().anyMatch(ComponentManifest::unsafeToken)) {
            throw new IllegalArgumentException("Component entrypoint is invalid");
        }
        if (kind == Kind.PACK && (!entrypoint.isEmpty() || !operations.isEmpty())) {
            throw new IllegalArgumentException("Pack components cannot execute code");
        }
        if (kind != Kind.PACK && entrypoint.isEmpty()) throw new IllegalArgumentException("Executable component requires an entrypoint");
        if (timeoutMillis < 100 || timeoutMillis > 30_000) {
            throw new IllegalArgumentException("Component timeoutMillis must be between 100 and 30000");
        }
        if (failureMode == null) throw new IllegalArgumentException("Component failureMode is required");
        if (spdxLicense == null || !spdxLicense.matches("[A-Za-z0-9.+-]{2,64}")) {
            throw new IllegalArgumentException("Component SPDX license identifier is required");
        }
        for (String permission : permissions) {
            if (!permission.matches("[a-z][a-z0-9._-]{1,63}")) {
                throw new IllegalArgumentException("Component permission is invalid");
            }
        }
    }

    public static ComponentManifest read(Path path) throws java.io.IOException {
        return from(Json.object(java.nio.file.Files.readString(path, java.nio.charset.StandardCharsets.UTF_8)));
    }

    public static ComponentManifest from(Map<String, Object> root) {
        Map<String, Object> metadata = object(root.get("metadata"), "metadata");
        Map<String, Object> spec = object(root.get("spec"), "spec");
        Map<String, Object> license = object(root.get("license"), "license");
        return new ComponentManifest(text(root, "apiVersion"), Kind.parse(text(root, "kind")),
                text(metadata, "id"), text(metadata, "version"), strings(spec.get("entrypoint")),
                Set.copyOf(strings(spec.get("operations"))), Set.copyOf(strings(spec.get("permissions"))),
                integer(spec.get("timeoutMillis"), 2_000), FailureMode.parse(text(spec, "failureMode")),
                text(license, "spdx"));
    }

    public List<String> command(Path componentDirectory) {
        String root = componentDirectory.toAbsolutePath().normalize().toString();
        return entrypoint.stream().map(value -> value.replace("${componentDir}", root)).toList();
    }

    public Map<String, Object> map() {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("apiVersion", apiVersion);
        value.put("kind", kind.name().toLowerCase(Locale.ROOT));
        value.put("id", id);
        value.put("version", version);
        value.put("operations", operations.stream().sorted().toList());
        value.put("permissions", permissions.stream().sorted().toList());
        value.put("timeoutMillis", timeoutMillis);
        value.put("failureMode", failureMode.externalName());
        value.put("license", spdxLicense);
        return value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value, String name) {
        if (value instanceof Map<?, ?> map) return (Map<String, Object>) map;
        throw new IllegalArgumentException("Component manifest requires " + name);
    }

    private static String text(Map<String, Object> value, String key) {
        Object raw = value.get(key);
        if (!(raw instanceof String text) || text.isBlank()) throw new IllegalArgumentException("Component manifest requires " + key);
        return text.trim();
    }

    private static List<String> strings(Object value) {
        if (value == null) return List.of();
        if (!(value instanceof List<?> list)) throw new IllegalArgumentException("Component manifest value must be an array");
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof String text) || text.isBlank()) throw new IllegalArgumentException("Component manifest array contains an invalid value");
            result.add(text);
        }
        return List.copyOf(result);
    }

    private static int integer(Object value, int fallback) {
        if (value == null) return fallback;
        if (value instanceof Number number) return number.intValue();
        throw new IllegalArgumentException("Component timeoutMillis must be an integer");
    }

    private static boolean unsafeToken(String value) {
        return value == null || value.isBlank() || value.length() > 4096 || value.indexOf('\0') >= 0
                || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0;
    }

    public enum Kind {
        PACK,
        PROVIDER,
        SANDBOX;

        private static Kind parse(String value) {
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException error) {
                throw new IllegalArgumentException("Component kind must be pack, provider, or sandbox");
            }
        }
    }

    public enum FailureMode {
        FAIL_OPEN("fail_open"),
        FAIL_CLOSED("fail_closed");

        private final String externalName;

        FailureMode(String externalName) {
            this.externalName = externalName;
        }

        public String externalName() {
            return externalName;
        }

        private static FailureMode parse(String value) {
            String normalized = value.trim().toLowerCase(Locale.ROOT);
            for (FailureMode mode : values()) if (mode.externalName.equals(normalized)) return mode;
            throw new IllegalArgumentException("Component failureMode must be fail_open or fail_closed");
        }
    }
}
