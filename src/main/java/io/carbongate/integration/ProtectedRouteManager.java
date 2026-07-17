package io.carbongate.integration;

import io.carbongate.mcp.McpProfileService;
import io.carbongate.mcp.McpProfileStore;
import io.carbongate.security.SecretScanner;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ProtectedRouteManager {
    private final McpProfileStore profiles;
    private final McpProfileService exports;
    private final ProtectedRouteRegistry registry;
    private final CommandRunner runner;
    private final SecretScanner secrets = new SecretScanner();

    public ProtectedRouteManager(Path home, CommandRunner runner) {
        profiles = new McpProfileStore(home);
        exports = new McpProfileService(profiles);
        registry = new ProtectedRouteRegistry(home);
        this.runner = runner;
    }

    public Map<String, Object> protect(String requestedHost, String requestedName, Path workspace,
                                       List<String> serverCommand, boolean dryRun)
            throws IOException, InterruptedException {
        String host = host(requestedHost);
        String name = name(requestedName);
        String serverName = "carbongate-" + name;
        if (registry.entries().containsKey(host + ":" + name)) return result(host, name, serverName,
                "already_protected", false);
        if (dryRun) {
            profiles.validate(name, workspace, serverCommand);
            List<String> invocation = IntegrationInvocation.forArguments("mcp", "profile", "run", name);
            Map<String, Object> planned = result(host, name, serverName, "planned", false);
            planned.put("descriptor", Map.of("command", invocation.getFirst(),
                    "args", invocation.stream().skip(1).toList()));
            return planned;
        }
        profiles.put(name, workspace, serverCommand, false);
        if (host.equals("generic")) {
            registry.put(ProtectedRouteRegistry.Entry.create(host, name, serverName, workspace));
            Map<String, Object> protectedRoute = result(host, name, serverName, "protected", true);
            protectedRoute.put("descriptor", exports.export(name, "mcp-json"));
            return protectedRoute;
        }
        if (!runner.available("codex")) {
            profiles.remove(name);
            return result(host, name, serverName, "host_unavailable", false);
        }
        CommandRunner.Result listed = runner.run(List.of("codex", "mcp", "list"));
        if (!listed.succeeded()) {
            profiles.remove(name);
            return failure(host, name, serverName, "inspection_failed", listed);
        }
        if (contains(listed.output(), serverName)) {
            profiles.remove(name);
            return result(host, name, serverName, "conflict_external_registration", false);
        }
        List<String> invocation = IntegrationInvocation.forArguments("mcp", "profile", "run", name);
        List<String> add = new ArrayList<>(List.of("codex", "mcp", "add", serverName, "--"));
        add.addAll(invocation);
        CommandRunner.Result added = runner.run(List.copyOf(add));
        if (!added.succeeded()) {
            profiles.remove(name);
            return failure(host, name, serverName, "add_failed", added);
        }
        CommandRunner.Result verified = runner.run(List.of("codex", "mcp", "list"));
        if (!verified.succeeded() || !contains(verified.output(), serverName)) {
            runner.run(List.of("codex", "mcp", "remove", serverName));
            profiles.remove(name);
            return failure(host, name, serverName, "verification_failed_rolled_back", verified);
        }
        registry.put(ProtectedRouteRegistry.Entry.create(host, name, serverName, workspace));
        return result(host, name, serverName, "protected", true);
    }

    public Map<String, Object> unprotect(String requestedHost, String requestedName)
            throws IOException, InterruptedException {
        String host = host(requestedHost);
        String name = name(requestedName);
        ProtectedRouteRegistry.Entry entry = registry.entries().get(host + ":" + name);
        if (entry == null) return result(host, name, "carbongate-" + name, "not_managed", false);
        if (host.equals("codex")) {
            if (!runner.available("codex")) return result(host, name, entry.serverName(), "host_unavailable", false);
            CommandRunner.Result removed = runner.run(List.of("codex", "mcp", "remove", entry.serverName()));
            if (!removed.succeeded()) return failure(host, name, entry.serverName(), "remove_failed", removed);
        }
        registry.remove(host, name);
        profiles.remove(name);
        return result(host, name, entry.serverName(), "removed", true);
    }

    public List<Map<String, Object>> list() throws IOException {
        return registry.entries().values().stream().map(ProtectedRouteRegistry.Entry::map).toList();
    }

    public ProtectedRouteRegistry registry() {
        return registry;
    }

    private Map<String, Object> result(String host, String name, String serverName,
                                       String state, boolean changed) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("host", host);
        result.put("name", name);
        result.put("serverName", serverName);
        result.put("state", state);
        result.put("changed", changed);
        result.put("coverage", "mcp_only");
        result.put("coverageDescription", Coverage.MCP_ONLY.description());
        return result;
    }

    private Map<String, Object> failure(String host, String name, String serverName,
                                        String state, CommandRunner.Result command) {
        Map<String, Object> result = result(host, name, serverName,
                command.timedOut() ? state.replace("failed", "timed_out") : state, false);
        String diagnostic = secrets.scan(command.output() == null ? "" : command.output()).redacted()
                .replace('\n', ' ').replace('\r', ' ').trim();
        result.put("diagnostic", diagnostic.isBlank() ? "exit code " + command.exitCode()
                : diagnostic.substring(0, Math.min(diagnostic.length(), 512)));
        return result;
    }

    private boolean contains(String output, String serverName) {
        return output != null && output.toLowerCase(Locale.ROOT).contains(serverName.toLowerCase(Locale.ROOT));
    }

    private String host(String value) {
        String host = value == null ? "codex" : value.trim().toLowerCase(Locale.ROOT);
        if (!host.equals("codex") && !host.equals("generic")) {
            throw new IllegalArgumentException("Protected route host must be codex or generic");
        }
        return host;
    }

    private String name(String value) {
        String name = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!name.matches("[a-z0-9][a-z0-9._-]{0,63}")) {
            throw new IllegalArgumentException("Protected route name must match [a-z0-9][a-z0-9._-]{0,63}");
        }
        return name;
    }
}
