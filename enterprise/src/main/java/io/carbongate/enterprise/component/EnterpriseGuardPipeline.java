package io.carbongate.enterprise.component;

import io.carbongate.json.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Explicit enterprise orchestration; Core remains independent of optional components. */
public final class EnterpriseGuardPipeline {
    private final ComponentStore store;
    private final ProviderClient providers;

    public EnterpriseGuardPipeline(ComponentStore store, ProviderClient providers) {
        this.store = store;
        this.providers = providers;
    }

    public Map<String, Object> evaluate(Map<String, Object> request) throws IOException, InterruptedException {
        if (request.containsKey("_carbongate")) throw new IllegalArgumentException("Guard request uses a reserved field");
        String action = text(request.get("action"), "action", 128);
        String risk = risk(request.getOrDefault("risk", "medium"));
        String content = optionalText(request.get("content"), "content", 262_144);
        String eventId = UUID.randomUUID().toString();
        List<Map<String, Object>> steps = new ArrayList<>();
        String decision = "allow";

        Map<String, Object> inspectPayload = Map.of("action", action, "risk", risk, "text", content);
        for (ComponentManifest component : operation("inspect")) {
            Step step = call(component, "inspect", inspectPayload);
            steps.add(step.summary());
            decision = merge(decision, inspectDecision(step.decision()));
            if (decision.equals("deny")) break;
        }

        if (!decision.equals("deny")) {
            Map<String, Object> authorization = Map.of("action", action, "risk", risk,
                    "inspectionDecision", decision);
            for (ComponentManifest component : operation("authorize")) {
                Step step = call(component, "authorize", authorization);
                steps.add(step.summary());
                decision = merge(decision, authorizeDecision(step.decision()));
                if (decision.equals("deny")) break;
            }
        }

        Map<String, Object> sandboxResult = Map.of();
        Object rawSandbox = request.get("sandbox");
        if (rawSandbox != null && decision.equals("allow")) {
            Map<String, Object> sandbox = object(rawSandbox, "sandbox");
            String id = text(sandbox.get("componentId"), "sandbox componentId", 64);
            Map<String, Object> payload = object(sandbox.get("payload"), "sandbox payload");
            ComponentManifest component = store.requireActive(id);
            if (!component.operations().contains("sandbox")) {
                throw new IllegalArgumentException("Selected component does not provide sandbox");
            }
            Step step = call(component, "sandbox", payload);
            steps.add(step.summary());
            if (step.failedClosed()) decision = "deny";
            else sandboxResult = step.result();
        }

        for (ComponentManifest component : operation("audit")) {
            Map<String, Object> auditEvent = Map.of("eventId", eventId, "action", action, "risk", risk,
                    "decision", decision, "components", summaryIds(steps));
            Step step = call(component, "audit", auditEvent);
            steps.add(step.summary());
            if (step.failedClosed()) decision = "deny";
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("apiVersion", "carbongate.guard/v1");
        result.put("eventId", eventId);
        result.put("decision", decision);
        result.put("steps", List.copyOf(steps));
        if (!sandboxResult.isEmpty()) result.put("sandbox", sandboxResult);
        return Map.copyOf(result);
    }

    private List<ComponentManifest> operation(String operation) throws IOException {
        return store.activeComponents().stream().filter(value -> value.operations().contains(operation)).toList();
    }

    private Step call(ComponentManifest component, String operation, Map<String, Object> payload)
            throws InterruptedException {
        try {
            Map<String, Object> response = providers.call(component,
                    store.componentDirectory(component.id(), component.version()), operation,
                    providerPayload(component, payload));
            Map<String, Object> result = object(response.get("result"), "Provider result");
            String decision = result.get("decision") instanceof String value ? value : "allow";
            return Step.success(component.id(), operation, decision, result);
        } catch (IOException | RuntimeException error) {
            boolean closed = component.failureMode() == ComponentManifest.FailureMode.FAIL_CLOSED;
            return Step.failure(component.id(), operation, closed, compact(error));
        }
    }

    private Map<String, Object> providerPayload(ComponentManifest component, Map<String, Object> payload)
            throws IOException {
        Map<String, Object> result = new LinkedHashMap<>(payload);
        if (!component.permissions().contains("packs.read")) return Map.copyOf(result);
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

    private List<String> summaryIds(List<Map<String, Object>> steps) {
        return steps.stream().map(value -> value.get("component") + ":" + value.get("operation")).toList();
    }

    private String inspectDecision(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "block", "deny" -> "deny";
            case "review", "ask" -> "ask";
            default -> "allow";
        };
    }

    private String authorizeDecision(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "deny", "block" -> "deny";
            case "ask", "review" -> "ask";
            default -> "allow";
        };
    }

    private String merge(String current, String next) {
        if (current.equals("deny") || next.equals("deny")) return "deny";
        if (current.equals("ask") || next.equals("ask")) return "ask";
        return "allow";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> object(Object value, String name) {
        if (value instanceof Map<?, ?> map) return (Map<String, Object>) map;
        throw new IllegalArgumentException(name + " must be an object");
    }

    private String text(Object value, String name, int limit) {
        if (!(value instanceof String text) || text.isBlank() || text.length() > limit) {
            throw new IllegalArgumentException(name + " is required and must be at most " + limit + " characters");
        }
        return text;
    }

    private String optionalText(Object value, String name, int limit) {
        if (value == null) return "";
        if (!(value instanceof String text) || text.length() > limit) {
            throw new IllegalArgumentException(name + " must be text of at most " + limit + " characters");
        }
        return text;
    }

    private String risk(Object value) {
        String risk = text(value, "risk", 16).toLowerCase(Locale.ROOT);
        if (!List.of("low", "medium", "high", "critical").contains(risk)) {
            throw new IllegalArgumentException("risk must be low, medium, high, or critical");
        }
        return risk;
    }

    private String compact(Throwable error) {
        String value = error.getMessage();
        if (value == null || value.isBlank()) value = error.getClass().getSimpleName();
        value = value.replace('\n', ' ').replace('\r', ' ').trim();
        return value.substring(0, Math.min(value.length(), 256));
    }

    private record Step(String component, String operation, String status, String decision,
                        boolean failedClosed, String message, Map<String, Object> result) {
        private static Step success(String component, String operation, String decision, Map<String, Object> result) {
            return new Step(component, operation, "ok", decision, false, "", Map.copyOf(result));
        }

        private static Step failure(String component, String operation, boolean closed, String message) {
            return new Step(component, operation, closed ? "failed_closed" : "failed_open",
                    closed ? "deny" : "allow", closed, message, Map.of());
        }

        private Map<String, Object> summary() {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("component", component);
            value.put("operation", operation);
            value.put("status", status);
            value.put("decision", decision);
            if (!message.isEmpty()) value.put("message", message);
            return Map.copyOf(value);
        }
    }
}
