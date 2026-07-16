package io.carbongate.gateway;

import io.carbongate.model.Action;
import io.carbongate.model.Capability;
import io.carbongate.model.Evaluation;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class EvaluationMapper {
    private EvaluationMapper() {}

    public static Action actionFrom(Map<String, Object> json, Path defaultWorkspace) {
        return new Action(
                text(json, "actor", "api-client"),
                Capability.parse(text(json, "capability", "unknown")),
                text(json, "operation", "unknown"),
                text(json, "resource", ""),
                // The gateway owns the trust boundary. A caller must not widen it
                // by supplying a different workspace in an untrusted request.
                defaultWorkspace,
                Map.of()
        );
    }

    public static Map<String, Object> toMap(Evaluation evaluation) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", evaluation.id());
        result.put("decision", evaluation.decision().name().toLowerCase());
        result.put("risk", evaluation.risk().name().toLowerCase());
        result.put("reason", evaluation.reason());
        result.put("findings", evaluation.findings());
        result.put("sanitizedResource", evaluation.sanitizedResource());
        result.put("evaluatedAt", evaluation.evaluatedAt().toString());
        return result;
    }

    private static String text(Map<String, Object> json, String key, String fallback) {
        Object value = json.get(key);
        return value == null ? fallback : String.valueOf(value);
    }
}
