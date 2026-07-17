package io.carbongate.provider.approval;

import java.util.Map;

public final class ApprovalPolicyProviderTest {
    public static void main(String[] args) {
        assert decision("read", "low").equals("allow");
        assert decision("shell", "medium").equals("ask");
        assert decision("read", "high").equals("ask");
        assert decision("read", "critical").equals("deny");
        Map<String, Object> inspected = ApprovalPolicyProvider.authorize(Map.of("action", "read", "risk", "low",
                "inspectionDecision", "deny"));
        assert inspected.get("decision").equals("deny");
        assert inspected.get("reasonCode").equals("inspection_denied");
        System.out.println("Approval Policy Provider tests passed.");
    }

    private static String decision(String action, String risk) {
        return String.valueOf(ApprovalPolicyProvider.authorize(Map.of("action", action, "risk", risk,
                "inspectionDecision", "allow")).get("decision"));
    }
}
