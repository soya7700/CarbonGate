package io.carbongate.provider.approval;

import io.carbongate.json.Json;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Stateless authorization policy; an ask decision is not an approval receipt. */
public final class ApprovalPolicyProvider {
    private static final String API_VERSION = "carbongate.provider/v1";
    private static final Set<String> MEDIUM_APPROVAL_ACTIONS = Set.of("shell", "filesystem", "egress", "network", "sandbox");

    private ApprovalPolicyProvider() {}

    public static void main(String[] args) throws Exception {
        Map<String, Object> request = Json.object(new String(System.in.readAllBytes(), StandardCharsets.UTF_8).trim());
        String id = text(request.get("id"), "request id", 128);
        String operation = text(request.get("operation"), "operation", 32);
        Map<String, Object> result = switch (operation) {
            case "health" -> Map.of("health", "pass", "policy", "approval-default-v1");
            case "authorize" -> authorize(object(request.get("payload"), "payload"));
            default -> throw new IllegalArgumentException("Unsupported operation");
        };
        System.out.println(Json.stringify(Map.of("apiVersion", API_VERSION, "id", id,
                "status", "ok", "result", result)));
    }

    static Map<String, Object> authorize(Map<String, Object> payload) {
        String action = text(payload.get("action"), "action", 128).toLowerCase(Locale.ROOT);
        String risk = text(payload.get("risk"), "risk", 16).toLowerCase(Locale.ROOT);
        String inspection = String.valueOf(payload.getOrDefault("inspectionDecision", "allow"))
                .toLowerCase(Locale.ROOT);
        String decision;
        String reason;
        if (inspection.equals("deny") || inspection.equals("block")) {
            decision = "deny";
            reason = "inspection_denied";
        } else if (risk.equals("critical")) {
            decision = "deny";
            reason = "critical_risk_denied";
        } else if (risk.equals("high")) {
            decision = "ask";
            reason = "high_risk_requires_approval";
        } else if (risk.equals("medium") && MEDIUM_APPROVAL_ACTIONS.contains(action)) {
            decision = "ask";
            reason = "sensitive_action_requires_approval";
        } else if (risk.equals("low") || risk.equals("medium")) {
            decision = "allow";
            reason = "policy_allowed";
        } else {
            throw new IllegalArgumentException("risk must be low, medium, high, or critical");
        }
        return Map.of("decision", decision, "reasonCode", reason,
                "approvalRequired", decision.equals("ask"), "suggestedTtlSeconds", decision.equals("ask") ? 300 : 0,
                "policy", "approval-default-v1");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value, String name) {
        if (value instanceof Map<?, ?> map) return (Map<String, Object>) map;
        throw new IllegalArgumentException(name + " must be an object");
    }

    private static String text(Object value, String name, int limit) {
        if (value instanceof String text && !text.isBlank() && text.length() <= limit) return text;
        throw new IllegalArgumentException(name + " is required or too long");
    }
}
