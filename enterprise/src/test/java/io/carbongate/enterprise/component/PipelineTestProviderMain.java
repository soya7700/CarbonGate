package io.carbongate.enterprise.component;

import io.carbongate.json.Json;

import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class PipelineTestProviderMain {
    public static void main(String[] args) throws Exception {
        Map<String, Object> request = Json.object(new String(System.in.readAllBytes(), StandardCharsets.UTF_8).trim());
        String id = String.valueOf(request.get("id"));
        String operation = String.valueOf(request.get("operation"));
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) request.get("payload");
        Map<String, Object> result = switch (operation) {
            case "health" -> Map.of("health", "pass");
            case "inspect" -> inspect(payload);
            case "authorize" -> Map.of("decision", "high".equals(payload.get("risk")) ? "ask" : "allow");
            case "audit" -> Map.of("stored", true);
            case "sandbox" -> Map.of("exitCode", 0, "profile", "test-sandbox");
            default -> throw new IllegalArgumentException("unsupported test operation");
        };
        System.out.println(Json.stringify(Map.of("apiVersion", ProviderClient.API_VERSION, "id", id,
                "status", "ok", "result", result)));
    }

    private static Map<String, Object> inspect(Map<String, Object> payload) {
        String text = String.valueOf(payload.get("text"));
        if (text.contains("fail")) {
            System.err.println("intentional pipeline test failure");
            System.exit(3);
        }
        return Map.of("decision", text.contains("blocked") ? "block" : "allow", "findingCount", 0);
    }
}
