package io.carbongate.runtime;

import io.carbongate.audit.AuditSink;
import io.carbongate.audit.EnterpriseAuditLog;
import io.carbongate.audit.SecurityEventLog;
import io.carbongate.authorization.AuthorizationStore;
import io.carbongate.config.AuditMode;
import io.carbongate.config.SettingsStore;
import io.carbongate.policy.PolicyProfile;

import java.nio.file.Path;

public final class CarbonGateRuntime {
    private CarbonGateRuntime() {}

    public static Instance fromConfig(Path stateHome, PolicyProfile profile) {
        SettingsStore settings = new SettingsStore(stateHome);
        AuthorizationStore approvals = new AuthorizationStore(stateHome);
        AuditSink audit = settings.auditMode() == AuditMode.ENTERPRISE_DETAILED
                ? new EnterpriseAuditLog(settings.enterpriseDirectory(), settings.enterpriseDailyLimitBytes())
                : new SecurityEventLog(stateHome, settings.localDailyLimitBytes());
        GuardService guard = new GuardService(profile, settings, approvals, audit);
        return new Instance(settings, approvals, audit, guard);
    }

    public static Instance enterprise(Path stateHome, Path auditDirectory, PolicyProfile profile,
                                      long dailyAuditLimitBytes) {
        SettingsStore settings = new SettingsStore(stateHome);
        AuthorizationStore approvals = new AuthorizationStore(stateHome);
        EnterpriseAuditLog audit = new EnterpriseAuditLog(auditDirectory, dailyAuditLimitBytes);
        GuardService guard = new GuardService(profile, settings, approvals, audit);
        return new Instance(settings, approvals, audit, guard);
    }

    public record Instance(SettingsStore settings, AuthorizationStore approvals,
                           AuditSink audit, GuardService guard) {}
}
