package io.carbongate.integration;

import io.carbongate.audit.SecurityEventLog;
import io.carbongate.config.SettingsStore;
import io.carbongate.mcp.McpProfileStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class InstallationDoctor {
    private final Path home;
    private final IntegrationManager integrations;
    private final List<String> invocation;

    public InstallationDoctor(Path home, IntegrationManager integrations, List<String> invocation) {
        this.home = home.toAbsolutePath().normalize();
        this.integrations = integrations;
        this.invocation = List.copyOf(invocation);
    }

    public Map<String, Object> diagnose() throws IOException, InterruptedException {
        List<Map<String, Object>> checks = new ArrayList<>();
        checks.add(javaCheck());
        checks.add(homeCheck());
        checks.add(configurationCheck());
        checks.add(logLimitCheck());
        checks.add(invocationCheck());
        checks.add(registryCheck());
        checks.add(mcpProfileRegistryCheck());
        checks.add(protectionRegistryCheck());
        List<Map<String, Object>> hostChecks;
        try {
            hostChecks = integrations.doctor();
        } catch (IOException | RuntimeException error) {
            hostChecks = List.of(Map.of("host", "registry", "state", "managed_registry_invalid",
                    "diagnostic", compact(error.getMessage())));
        }
        boolean systemHealthy = checks.stream().noneMatch(this::failed);
        boolean integrationsHealthy = hostChecks.stream().noneMatch(this::unhealthyIntegration);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("healthy", systemHealthy && integrationsHealthy);
        result.put("carbonHome", home.toString());
        result.put("registry", integrations.registry().path().toString());
        result.put("checks", List.copyOf(checks));
        result.put("integrations", hostChecks);
        return result;
    }

    private Map<String, Object> javaCheck() {
        String version = System.getProperty("java.specification.version", "unknown");
        boolean valid;
        try {
            valid = Integer.parseInt(version) >= 21;
        } catch (NumberFormatException ignored) {
            valid = false;
        }
        return check("java", valid ? "pass" : "fail", "Java " + version + (valid ? " satisfies JDK 21+" : " does not satisfy JDK 21+"));
    }

    private Map<String, Object> homeCheck() {
        if (Files.exists(home)) {
            boolean valid = Files.isDirectory(home) && Files.isReadable(home) && Files.isWritable(home);
            return check("carbon_home", valid ? "pass" : "fail", valid
                    ? "state directory is readable and writable" : "state path must be a readable and writable directory");
        }
        Path parent = existingParent(home);
        boolean writable = parent != null && Files.isWritable(parent);
        return check("carbon_home", writable ? "warn" : "fail", writable
                ? "state directory is not initialized; its parent is writable"
                : "state directory is missing and cannot be created by the current user");
    }

    private Map<String, Object> configurationCheck() {
        SettingsStore settings = new SettingsStore(home);
        try {
            if (!Files.isRegularFile(settings.path())) {
                return check("configuration", "warn", "configuration uses defaults; run carbon config init");
            }
            List<String> diagnostics = settings.diagnostics();
            if (!diagnostics.isEmpty()) {
                return check("configuration", "fail", "configuration diagnostics: " + compact(String.join("; ", diagnostics)));
            }
            settings.snapshot();
            return check("configuration", "pass", "configuration is readable and valid");
        } catch (IOException | RuntimeException error) {
            return check("configuration", "fail", "configuration cannot be read: " + compact(error.getMessage()));
        }
    }

    private Map<String, Object> logLimitCheck() {
        try {
            long limit = new SettingsStore(home).localDailyLimitBytes();
            boolean valid = limit > 0 && limit <= SecurityEventLog.DEFAULT_DAILY_LIMIT_BYTES;
            return check("local_log_limit", valid ? "pass" : "fail",
                    "local daily disk budget is " + limit + " bytes");
        } catch (RuntimeException error) {
            return check("local_log_limit", "fail", "local log limit is invalid: " + compact(error.getMessage()));
        }
    }

    private Map<String, Object> invocationCheck() {
        if (invocation.isEmpty()) return check("control_invocation", "fail", "control invocation is empty");
        int jarFlag = invocation.indexOf("-jar");
        if (jarFlag >= 0 && jarFlag + 1 < invocation.size()) {
            boolean exists = Files.isRegularFile(Path.of(invocation.get(jarFlag + 1)));
            return check("control_invocation", exists ? "pass" : "fail",
                    exists ? "CarbonGate JAR is available" : "CarbonGate JAR is missing");
        }
        Path command = Path.of(invocation.getFirst());
        boolean valid = !command.isAbsolute() || Files.isRegularFile(command);
        return check("control_invocation", valid ? "pass" : "fail", valid
                ? "control command is available through the launcher or PATH" : "control command is missing");
    }

    private Map<String, Object> registryCheck() {
        try {
            int count = integrations.registry().entries().size();
            return check("integration_registry", "pass", "registry is readable; managed hosts: " + count);
        } catch (IOException | RuntimeException error) {
            return check("integration_registry", "fail", "registry cannot be read: " + compact(error.getMessage()));
        }
    }

    private Map<String, Object> mcpProfileRegistryCheck() {
        McpProfileStore profiles = new McpProfileStore(home);
        try {
            List<McpProfileStore.Profile> routes = profiles.list();
            long missing = routes.stream().filter(route -> !Files.isDirectory(route.workspace())).count();
            if (missing > 0) {
                return check("mcp_profile_registry", "fail", "protected MCP profile registry has "
                        + missing + " route(s) with a missing workspace");
            }
            int count = routes.size();
            return check("mcp_profile_registry", "pass", "protected MCP profile registry is readable; routes: " + count);
        } catch (IOException | RuntimeException error) {
            return check("mcp_profile_registry", "fail", "protected MCP profile registry cannot be read: "
                    + compact(error.getMessage()));
        }
    }

    private Map<String, Object> protectionRegistryCheck() {
        ProtectedRouteRegistry protections = new ProtectedRouteRegistry(home);
        try {
            int count = protections.entries().size();
            return check("protection_registry", "pass", "protected route registry is readable; routes: " + count);
        } catch (IOException | RuntimeException error) {
            return check("protection_registry", "fail", "protected route registry cannot be read: "
                    + compact(error.getMessage()));
        }
    }

    private Map<String, Object> check(String name, String status, String message) {
        return Map.of("name", name, "status", status, "message", message);
    }

    private boolean failed(Map<String, Object> check) {
        return "fail".equals(check.get("status"));
    }

    private boolean unhealthyIntegration(Map<String, Object> integration) {
        String state = String.valueOf(integration.get("state"));
        return state.startsWith("managed_") || state.equals("external_registration");
    }

    private Path existingParent(Path value) {
        Path current = value;
        while (current != null && !Files.exists(current)) current = current.getParent();
        return current;
    }

    private String compact(String value) {
        if (value == null || value.isBlank()) return "unknown error";
        String compacted = value.replace('\n', ' ').replace('\r', ' ').trim();
        return compacted.length() <= 256 ? compacted : compacted.substring(0, 256) + "…";
    }
}
