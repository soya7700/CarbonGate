package io.carbongate.model;

import java.time.Instant;
import java.util.List;

public record Evaluation(
        String id,
        Decision decision,
        RiskLevel risk,
        String reason,
        List<String> findings,
        String sanitizedResource,
        Instant evaluatedAt
) {
    public Evaluation {
        findings = findings == null ? List.of() : List.copyOf(findings);
    }
}
