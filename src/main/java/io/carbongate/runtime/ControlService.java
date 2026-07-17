package io.carbongate.runtime;

import io.carbongate.audit.EnterpriseAuditLog;
import io.carbongate.audit.SecurityEventLog;
import io.carbongate.config.SettingsStore;
import io.carbongate.policy.EnforcementMode;
import io.carbongate.policy.PolicyProfile;
import io.carbongate.security.ShellRiskAnalyzer;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ControlService {
    private final CarbonGateRuntime.Instance runtime;
    private final PolicyProfile profile;

    public ControlService(Path home, PolicyProfile profile) {
        this.runtime = CarbonGateRuntime.fromConfig(home, profile);
        this.profile = profile;
    }

    public Map<String, Object> status() {
        SettingsStore settings = runtime.settings();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mode", settings.mode().name().toLowerCase());
        result.put("modeDescription", settings.mode().description());
        result.put("profile", profile.name().toLowerCase());
        result.put("carbonHome", settings.home().toString());
        result.put("settings", settings.path().toString());
        result.put("auditMode", settings.auditMode().name().toLowerCase());
        result.put("alertLocation", "host conversation/tool response and terminal stderr");
        result.put("manualApproval", "carbon approvals list / approve / deny, or CarbonGate MCP approval tools");
        result.put("pendingApprovals", runtime.approvals().pendingCount());
        if (runtime.audit() instanceof SecurityEventLog events) {
            SecurityEventLog.DailyStats stats = events.todayStats();
            result.put("blockedLog", events.blockedPath().toString());
            result.put("errorLog", events.errorPath().toString());
            result.put("logLevel", "ERROR only");
            result.put("todayBlockedEvents", stats.blockedEvents());
            result.put("todayInternalErrors", stats.internalErrors());
            result.put("todayLogBytes", stats.bytesWritten());
            result.put("dailyLogByteLimit", stats.byteLimit());
        } else if (runtime.audit() instanceof EnterpriseAuditLog enterprise) {
            EnterpriseAuditLog.Stats stats = enterprise.todayStats();
            result.put("auditLog", enterprise.todayPath().toString());
            result.put("logLevel", "INFO/WARN/ERROR detailed decisions");
            result.put("todayAllowed", stats.allowed());
            result.put("todayPendingApproval", stats.pendingApproval());
            result.put("todayDenied", stats.denied());
            result.put("todayInternalErrors", stats.internalErrors());
            result.put("todayLogBytes", stats.bytesWritten());
            result.put("dailyLogByteLimit", stats.byteLimit());
        }
        return result;
    }

    public Map<String, Object> rules() {
        SettingsStore settings = runtime.settings();
        var configured = settings.rules();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("activeMode", settings.mode().name().toLowerCase());
        result.put("auditMode", settings.auditMode().name().toLowerCase());
        result.put("enabled", Map.of("shell", configured.shell(), "filesystem", configured.filesystem(),
                "network", configured.network(), "secrets", configured.secrets()));
        result.put("modes", List.of(
                Map.of("name", "balanced", "behavior", EnforcementMode.BALANCED.description()),
                Map.of("name", "warn", "behavior", EnforcementMode.WARN.description()),
                Map.of("name", "approval", "behavior", EnforcementMode.APPROVAL.description()),
                Map.of("name", "block", "behavior", EnforcementMode.BLOCK.description())));
        result.put("shellRules", ShellRiskAnalyzer.catalog());
        result.put("filesystemRules", List.of("read inside workspace: low", "write: medium", "delete: high",
                "path traversal or symlink escape: critical"));
        result.put("networkRules", List.of("external GET: medium", "external write: high",
                "localhost/private/cloud metadata or secret egress: critical"));
        result.put("logging", settings.auditMode() == io.carbongate.config.AuditMode.LOCAL_MINIMAL
                ? "local: compact denied actions and internal errors only, capped at 10,000,000 bytes/day"
                : "enterprise: detailed allow, warning, approval, denial and error audit records");
        return result;
    }

    public Map<String, Object> blocked(int requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit, 100));
        List<Map<String, Object>> events = runtime.audit() instanceof SecurityEventLog local
                ? local.recentBlocked(limit)
                : runtime.audit() instanceof EnterpriseAuditLog enterprise
                ? enterprise.recentBlocked(limit) : List.of();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("auditMode", runtime.settings().auditMode().name().toLowerCase());
        result.put("path", runtime.audit() instanceof SecurityEventLog local ? local.blockedPath().toString()
                : runtime.audit() instanceof EnterpriseAuditLog enterprise ? enterprise.todayPath().toString() : "");
        result.put("events", events);
        return result;
    }

    public Map<String, Object> approvals() throws IOException {
        return Map.of("pending", runtime.approvals().pending());
    }

    public Map<String, Object> approve(String id) {
        boolean changed = runtime.guard().approve(id);
        return Map.of("id", id, "status", changed ? "approved_once" : "not_found");
    }

    public Map<String, Object> deny(String id) {
        boolean changed = runtime.guard().denyApproval(id);
        return Map.of("id", id, "status", changed ? "denied" : "not_found");
    }

    public Map<String, Object> setMode(String instruction) throws IOException {
        SettingsStore settings = runtime.settings();
        EnforcementMode previous = settings.mode();
        EnforcementMode selected = EnforcementMode.fromNaturalLanguage(instruction);
        settings.setMode(selected);
        runtime.audit().recordControl("mode_changed", Map.of("previous", previous.name(),
                "mode", selected.name(), "instruction", instruction));
        return Map.of("previous", previous.name().toLowerCase(), "mode", selected.name().toLowerCase(),
                "description", selected.description(), "effective", "next tool call");
    }

    public void recordError(String component, String message) {
        runtime.audit().recordError(component, message);
    }
}
