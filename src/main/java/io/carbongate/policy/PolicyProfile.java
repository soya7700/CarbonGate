package io.carbongate.policy;

import io.carbongate.model.Decision;
import io.carbongate.model.RiskLevel;

public enum PolicyProfile {
    STRICT,
    BALANCED,
    AUDIT;

    public static PolicyProfile parse(String value) {
        if (value == null || value.isBlank()) return BALANCED;
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            throw new IllegalArgumentException("Unknown policy profile: " + value);
        }
    }

    public Decision decide(RiskLevel risk) {
        return switch (this) {
            case STRICT -> switch (risk) {
                case LOW -> Decision.ALLOW;
                case MEDIUM -> Decision.ASK;
                case HIGH, CRITICAL -> Decision.DENY;
            };
            case BALANCED -> switch (risk) {
                case LOW -> Decision.ALLOW;
                case MEDIUM, HIGH -> Decision.ASK;
                case CRITICAL -> Decision.DENY;
            };
            case AUDIT -> risk == RiskLevel.CRITICAL ? Decision.ASK : Decision.ALLOW;
        };
    }
}
