package io.carbongate.runtime;

import io.carbongate.audit.SecurityEventLog;
import io.carbongate.authorization.AuthorizationStore;
import io.carbongate.config.SettingsStore;
import io.carbongate.model.Action;
import io.carbongate.model.Decision;
import io.carbongate.policy.EnforcementMode;
import io.carbongate.policy.PolicyProfile;

import java.nio.file.Files;
import java.nio.file.Path;

public final class GuardServiceTest {
    public static void run() throws Exception {
        Path home = Files.createTempDirectory("carbon-guard-");
        SettingsStore settings = new SettingsStore(home);
        settings.setMode(EnforcementMode.APPROVAL);
        AuthorizationStore approvals = new AuthorizationStore(home);
        SecurityEventLog events = new SecurityEventLog(home);
        GuardService guard = new GuardService(PolicyProfile.BALANCED, settings, approvals, events);
        Action action = Action.shell("git status", home);

        var pending = guard.evaluate(action);
        assert pending.decision() == Decision.ASK;
        assert approvals.pendingCount() == 1;
        assert events.todayStats().bytesWritten() == 0;

        assert approvals.approve(pending.id());
        assert guard.evaluate(action).decision() == Decision.ALLOW;
        assert guard.evaluate(action).decision() == Decision.ASK;

        settings.setMode(EnforcementMode.BLOCK);
        var denied = guard.evaluate(action);
        assert denied.decision() == Decision.DENY;
        assert events.todayStats().blockedEvents() == 1;
    }
}
