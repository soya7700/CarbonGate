package io.carbongate.runtime;

import io.carbongate.audit.AuditSink;
import io.carbongate.authorization.AuthorizationStore;
import io.carbongate.config.SettingsStore;
import io.carbongate.model.Action;
import io.carbongate.model.Decision;
import io.carbongate.model.Evaluation;
import io.carbongate.policy.EnforcementMode;
import io.carbongate.policy.PolicyEngine;
import io.carbongate.policy.PolicyProfile;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;

public final class GuardService {
    private final PolicyProfile profile;
    private final SettingsStore settings;
    private final AuthorizationStore approvals;
    private final AuditSink audit;

    public GuardService(PolicyProfile profile, SettingsStore settings,
                        AuthorizationStore approvals, AuditSink audit) {
        this.profile = profile;
        this.settings = settings;
        this.approvals = approvals;
        this.audit = audit;
    }

    public Evaluation evaluate(Action action) {
        EnforcementMode mode = settings.mode();
        Evaluation evaluated = new PolicyEngine(profile, mode, settings.rules()).evaluate(action);
        try {
            if (evaluated.decision() == Decision.ASK && approvals.consumeApproved(action)) {
                ArrayList<String> findings = new ArrayList<>(evaluated.findings());
                findings.add("one-time manual approval consumed");
                Evaluation approved = new Evaluation(UUID.randomUUID().toString(), Decision.ALLOW, evaluated.risk(),
                        "allow once by explicit manual approval", findings,
                        evaluated.sanitizedResource(), Instant.now());
                return withAudit(action, approved);
            }
            if (evaluated.decision() == Decision.ASK) {
                String id = approvals.ensurePending(action, evaluated);
                Evaluation pending = new Evaluation(id, Decision.ASK, evaluated.risk(),
                        evaluated.reason() + "; run `carbon approvals approve " + id + "` and retry",
                        evaluated.findings(), evaluated.sanitizedResource(), evaluated.evaluatedAt());
                return withAudit(action, pending);
            }
            return withAudit(action, evaluated);
        } catch (IOException | RuntimeException error) {
            audit.recordError("authorization", error.getMessage());
            Evaluation denied = new Evaluation(UUID.randomUUID().toString(), Decision.DENY, evaluated.risk(),
                    "deny because authorization state could not be persisted",
                    java.util.List.of("authorization storage error"), evaluated.sanitizedResource(), Instant.now());
            return withAudit(action, denied);
        }
    }

    public void discardApproval(String id) {
        try {
            approvals.deny(id);
        } catch (IOException | RuntimeException error) {
            audit.recordError("authorization", error.getMessage());
        }
    }

    public boolean approve(String id) {
        try {
            boolean changed = approvals.approve(id);
            if (changed) audit.recordControl("approval_granted_once", java.util.Map.of("id", id));
            return changed;
        } catch (IOException | RuntimeException error) {
            audit.recordError("authorization", error.getMessage());
            return false;
        }
    }

    public boolean denyApproval(String id) {
        try {
            boolean changed = approvals.deny(id);
            if (changed) audit.recordControl("approval_denied", java.util.Map.of("id", id));
            return changed;
        } catch (IOException | RuntimeException error) {
            audit.recordError("authorization", error.getMessage());
            return false;
        }
    }

    public EnforcementMode mode() {
        return settings.mode();
    }

    public int consoleDailyWarningLimit() {
        return settings.consoleDailyWarningLimit();
    }

    public AuditSink audit() {
        return audit;
    }

    private Evaluation withAudit(Action action, Evaluation evaluation) {
        if (audit.recordDecision(action, evaluation)) return evaluation;
        ArrayList<String> findings = new ArrayList<>(evaluation.findings());
        if (audit.requiresSuccessfulDecisionRecord()) {
            findings.add("enterprise audit write failed; action failed closed");
            return new Evaluation(UUID.randomUUID().toString(), Decision.DENY, evaluation.risk(),
                    "deny because required enterprise audit could not be written",
                    findings, evaluation.sanitizedResource(), Instant.now());
        }
        if (evaluation.decision() == Decision.DENY) {
            findings.add("blocked event not written because the local daily log cap was reached or logging failed");
            return new Evaluation(evaluation.id(), evaluation.decision(), evaluation.risk(),
                    evaluation.reason(), findings, evaluation.sanitizedResource(), evaluation.evaluatedAt());
        }
        return evaluation;
    }
}
