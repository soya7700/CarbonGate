package io.carbongate.security;

import io.carbongate.model.RiskLevel;

import java.util.List;

public record RiskAssessment(RiskLevel risk, List<String> findings) {
    public RiskAssessment {
        findings = findings == null ? List.of() : List.copyOf(findings);
    }
}
