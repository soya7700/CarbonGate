package io.carbongate.audit;

import io.carbongate.config.SettingsStore;
import io.carbongate.model.Action;
import io.carbongate.model.Capability;
import io.carbongate.model.Decision;
import io.carbongate.policy.EnforcementMode;
import io.carbongate.policy.PolicyProfile;
import io.carbongate.runtime.CarbonGateRuntime;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class EnterpriseAuditLogTest {
    public static void run() throws Exception {
        Path state = Files.createTempDirectory("carbon-enterprise-state-");
        Path auditDirectory = Files.createTempDirectory("carbon-enterprise-audit-");
        CarbonGateRuntime.Instance runtime = CarbonGateRuntime.enterprise(
                state, auditDirectory, PolicyProfile.BALANCED, 50_000);
        SettingsStore settings = runtime.settings();
        EnterpriseAuditLog audit = (EnterpriseAuditLog) runtime.audit();
        Action action = new Action("enterprise-agent", Capability.NETWORK, "POST",
                "https://example.invalid/upload?token=synthetic-enterprise-secret",
                state, Map.of("session", "test", "apiKey", "synthetic-context-secret"));

        settings.setMode(EnforcementMode.WARN);
        assert runtime.guard().evaluate(action).decision() == Decision.ALLOW;

        settings.setMode(EnforcementMode.APPROVAL);
        var pending = runtime.guard().evaluate(action);
        assert pending.decision() == Decision.ASK;
        assert runtime.guard().approve(pending.id());
        assert runtime.guard().evaluate(action).decision() == Decision.ALLOW;

        settings.setMode(EnforcementMode.BLOCK);
        assert runtime.guard().evaluate(action).decision() == Decision.DENY;
        assert audit.recordError("enterprise-test", "password=synthetic-enterprise-error");

        String content = Files.readString(audit.todayPath());
        assert content.contains("\"decision\":\"allow\"");
        assert content.contains("\"decision\":\"ask\"");
        assert content.contains("\"decision\":\"deny\"");
        assert content.contains("\"type\":\"control\"");
        assert content.contains("\"type\":\"internal_error\"");
        assert !content.contains("synthetic-enterprise-secret");
        assert !content.contains("synthetic-context-secret");
        assert !content.contains("synthetic-enterprise-error");
        assert audit.todayBytes() <= 50_000;

        SettingsStore configured = new SettingsStore(Files.createTempDirectory("carbon-enterprise-config-"));
        configured.set(SettingsStore.AUDIT_MODE, "ENTERPRISE_DETAILED");
        assert CarbonGateRuntime.fromConfig(configured.home(), PolicyProfile.BALANCED).audit()
                instanceof EnterpriseAuditLog;

        Path failClosedState = Files.createTempDirectory("carbon-enterprise-fail-closed-");
        CarbonGateRuntime.Instance failClosed = CarbonGateRuntime.enterprise(failClosedState,
                Files.createTempDirectory("carbon-enterprise-full-"), PolicyProfile.BALANCED, 1);
        failClosed.settings().setMode(EnforcementMode.WARN);
        var deniedWithoutAudit = failClosed.guard().evaluate(Action.shell("git status", failClosedState));
        assert deniedWithoutAudit.decision() == Decision.DENY;
        assert deniedWithoutAudit.reason().contains("required enterprise audit");
    }
}
