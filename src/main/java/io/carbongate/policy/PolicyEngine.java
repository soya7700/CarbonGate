package io.carbongate.policy;

import io.carbongate.model.Action;
import io.carbongate.model.Capability;
import io.carbongate.model.Decision;
import io.carbongate.model.Evaluation;
import io.carbongate.model.RiskLevel;
import io.carbongate.security.FileBoundary;
import io.carbongate.security.RiskAssessment;
import io.carbongate.security.SecretScanner;
import io.carbongate.security.ShellRiskAnalyzer;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class PolicyEngine {
    private final PolicyProfile profile;
    private final ShellRiskAnalyzer shellAnalyzer;
    private final SecretScanner secretScanner;

    public PolicyEngine(PolicyProfile profile) {
        this(profile, new ShellRiskAnalyzer(), new SecretScanner());
    }

    public PolicyEngine(PolicyProfile profile, ShellRiskAnalyzer shellAnalyzer, SecretScanner secretScanner) {
        this.profile = profile;
        this.shellAnalyzer = shellAnalyzer;
        this.secretScanner = secretScanner;
    }

    public Evaluation evaluate(Action action) {
        List<String> findings = new ArrayList<>();
        RiskLevel risk;

        if (action.capability() == Capability.SHELL) {
            RiskAssessment assessment = shellAnalyzer.analyze(action.resource());
            risk = assessment.risk();
            findings.addAll(assessment.findings());
        } else if (action.capability() == Capability.FILESYSTEM) {
            risk = evaluateFile(action, findings);
        } else if (action.capability() == Capability.NETWORK) {
            risk = evaluateNetwork(action, findings);
        } else {
            risk = RiskLevel.MEDIUM;
            findings.add("unknown capability requires authorization");
        }

        SecretScanner.ScanResult secrets = secretScanner.scan(action.resource());
        if (secrets.containsSecrets()) {
            findings.add("sensitive value detected: " + String.join(", ", secrets.findings()));
            if (action.capability() == Capability.NETWORK) risk = RiskLevel.CRITICAL;
            else if (risk.ordinal() < RiskLevel.HIGH.ordinal()) risk = RiskLevel.HIGH;
        }

        Decision decision = profile.decide(risk);
        String reason = reason(decision, risk, findings);
        return new Evaluation(
                UUID.randomUUID().toString(),
                decision,
                risk,
                reason,
                findings,
                secrets.redacted(),
                Instant.now()
        );
    }

    private RiskLevel evaluateFile(Action action, List<String> findings) {
        try {
            new FileBoundary(action.workspace()).resolve(action.resource());
        } catch (IOException | RuntimeException e) {
            findings.add(e.getMessage());
            return RiskLevel.CRITICAL;
        }
        String operation = action.operation().toLowerCase();
        if (operation.contains("delete")) {
            findings.add("file deletion within workspace");
            return RiskLevel.HIGH;
        }
        if (operation.contains("write") || operation.contains("create") || operation.contains("move")) {
            findings.add("file modification within workspace");
            return RiskLevel.MEDIUM;
        }
        findings.add("read within workspace");
        return RiskLevel.LOW;
    }

    private RiskLevel evaluateNetwork(Action action, List<String> findings) {
        String resource = action.resource().toLowerCase();
        if (resource.contains("127.0.0.1") || resource.contains("localhost") ||
                resource.contains("169.254.169.254") || resource.contains("metadata.google.internal")) {
            findings.add("local, private, or cloud metadata destination");
            return RiskLevel.CRITICAL;
        }
        if (action.operation().equalsIgnoreCase("GET")) {
            findings.add("external network read");
            return RiskLevel.MEDIUM;
        }
        findings.add("external network write");
        return RiskLevel.HIGH;
    }

    private String reason(Decision decision, RiskLevel risk, List<String> findings) {
        return decision.name().toLowerCase() + " by " + profile.name().toLowerCase() +
                " policy: " + risk.name().toLowerCase() + " risk; " + findings.getFirst();
    }
}
