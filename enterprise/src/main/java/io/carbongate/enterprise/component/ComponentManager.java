package io.carbongate.enterprise.component;

import io.carbongate.json.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ComponentManager {
    private final ComponentStore store;
    private final ProviderClient providers;

    public ComponentManager(Path carbonHome) {
        this(new ComponentStore(carbonHome), new ProviderClient());
    }

    public ComponentManager(ComponentStore store, ProviderClient providers) {
        this.store = store;
        this.providers = providers;
    }

    public Map<String, Object> install(Path archive) throws IOException {
        ComponentManifest manifest = store.install(archive);
        return Map.of("state", "installed_disabled", "component", manifest.map(),
                "directory", store.componentDirectory(manifest.id(), manifest.version()).toString());
    }

    public List<Map<String, Object>> list() throws IOException {
        return store.list();
    }

    public Map<String, Object> enable(String id, String version) throws IOException, InterruptedException {
        ComponentManifest manifest = store.require(id, version);
        Map<String, Object> health = health(manifest);
        store.activate(id, version);
        return Map.of("state", "enabled", "component", manifest.map(), "health", health);
    }

    public Map<String, Object> disable(String id) throws IOException {
        boolean changed = store.disable(id);
        return Map.of("state", changed ? "disabled" : "not_enabled", "id", id, "changed", changed);
    }

    public Map<String, Object> remove(String id, String version) throws IOException {
        store.remove(id, version);
        return Map.of("state", "removed", "id", id, "version", version);
    }

    public Map<String, Object> invoke(String id, String operation, Map<String, Object> payload)
            throws IOException, InterruptedException {
        ComponentManifest manifest = store.requireActive(id);
        return providers.call(manifest, store.componentDirectory(id, manifest.version()), operation,
                providerPayload(manifest, payload));
    }

    public Map<String, Object> doctor() throws IOException, InterruptedException {
        List<Map<String, Object>> components = new ArrayList<>();
        boolean healthy = true;
        for (Map<String, Object> installed : store.list()) {
            Map<String, Object> result = new LinkedHashMap<>(installed);
            if (!Boolean.TRUE.equals(installed.get("enabled"))) {
                result.put("health", "disabled");
            } else {
                try {
                    ComponentManifest manifest = store.require(String.valueOf(installed.get("id")),
                            String.valueOf(installed.get("version")));
                    result.put("health", health(manifest));
                } catch (IOException | RuntimeException error) {
                    healthy = false;
                    result.put("health", Map.of("status", "fail", "message", compact(error)));
                }
            }
            components.add(result);
        }
        return Map.of("healthy", healthy, "componentRoot", store.root().toString(),
                "components", List.copyOf(components));
    }

    private Map<String, Object> health(ComponentManifest manifest) throws IOException, InterruptedException {
        if (manifest.kind() == ComponentManifest.Kind.PACK) {
            PackDocument pack = PackDocument.read(store.componentDirectory(manifest.id(), manifest.version())
                    .resolve("payload").resolve("pack.json"));
            return Map.of("status", "pass", "mode", "data_only", "rules", pack.rules().size());
        }
        return providers.call(manifest, store.componentDirectory(manifest.id(), manifest.version()),
                "health", Map.of());
    }

    private Map<String, Object> providerPayload(ComponentManifest manifest, Map<String, Object> payload)
            throws IOException {
        Map<String, Object> result = new LinkedHashMap<>(payload == null ? Map.of() : payload);
        if (result.containsKey("_carbongate")) {
            throw new IllegalArgumentException("Provider payload cannot define reserved _carbongate context");
        }
        if (!manifest.permissions().contains("packs.read")) return Map.copyOf(result);
        List<Map<String, Object>> packs = new ArrayList<>();
        for (ComponentManifest pack : store.activeComponents(ComponentManifest.Kind.PACK)) {
            Path document = store.componentDirectory(pack.id(), pack.version()).resolve("payload").resolve("pack.json");
            PackDocument.read(document);
            packs.add(Map.of("id", pack.id(), "version", pack.version(), "document",
                    Json.object(Files.readString(document, StandardCharsets.UTF_8))));
        }
        result.put("_carbongate", Map.of("activePacks", List.copyOf(packs)));
        return Map.copyOf(result);
    }

    private String compact(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) message = error.getClass().getSimpleName();
        message = message.replace('\n', ' ').replace('\r', ' ').trim();
        return message.substring(0, Math.min(message.length(), 256));
    }
}
