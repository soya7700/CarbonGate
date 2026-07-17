package io.carbongate.integration;

import io.carbongate.security.SecretScanner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class IntegrationManager {
    private final IntegrationRegistry registry;
    private final CommandRunner runner;
    private final List<HostDefinition> catalog;
    private final List<String> invocation;
    private final SecretScanner secrets = new SecretScanner();

    public IntegrationManager(IntegrationRegistry registry, CommandRunner runner,
                              List<HostDefinition> catalog, List<String> invocation) {
        this.registry = registry;
        this.runner = runner;
        this.catalog = List.copyOf(catalog);
        this.invocation = List.copyOf(invocation);
        if (this.invocation.isEmpty()) throw new IllegalArgumentException("CarbonGate invocation cannot be empty");
    }

    public List<Map<String, Object>> list() throws IOException {
        Map<String, IntegrationRegistry.Entry> owned = registry.entries();
        List<Map<String, Object>> result = new ArrayList<>();
        for (HostDefinition host : catalog) {
            boolean available = runner.available(host.executable());
            Map<String, Object> value = base(host, available);
            value.put("managed", owned.containsKey(host.id()));
            value.put("state", !available ? "unavailable" : owned.containsKey(host.id()) ? "managed" : "available");
            result.add(value);
        }
        return List.copyOf(result);
    }

    public List<Map<String, Object>> setup(List<String> requestedHosts, boolean apply)
            throws IOException, InterruptedException {
        List<HostDefinition> selected = select(requestedHosts);
        List<Map<String, Object>> results = new ArrayList<>();
        for (HostDefinition host : selected) results.add(setup(host, apply));
        return List.copyOf(results);
    }

    public Map<String, Object> remove(String id) throws IOException, InterruptedException {
        HostDefinition host = require(id);
        Map<String, Object> result = base(host, runner.available(host.executable()));
        if (!registry.entries().containsKey(host.id())) {
            result.put("state", "not_managed");
            result.put("changed", false);
            return result;
        }
        if (!runner.available(host.executable())) {
            result.put("state", "unavailable");
            result.put("changed", false);
            return result;
        }
        CommandRunner.Result removed = runner.run(host.removeCommand());
        if (!removed.succeeded()) {
            result.put("state", removed.timedOut() ? "remove_timed_out" : "remove_failed");
            result.put("changed", false);
            result.put("diagnostic", diagnostic(removed));
            return result;
        }
        registry.remove(host.id());
        result.put("state", "removed");
        result.put("changed", true);
        return result;
    }

    public List<Map<String, Object>> doctor() throws IOException, InterruptedException {
        Map<String, IntegrationRegistry.Entry> owned = registry.entries();
        List<Map<String, Object>> results = new ArrayList<>();
        for (HostDefinition host : catalog) {
            boolean available = runner.available(host.executable());
            Map<String, Object> result = base(host, available);
            if (!available) {
                result.put("state", owned.containsKey(host.id()) ? "managed_host_missing" : "unavailable");
            } else {
                CommandRunner.Result listed = runner.run(host.listCommand());
                boolean present = listed.succeeded() && containsRegistration(listed.output());
                result.put("state", owned.containsKey(host.id())
                        ? present ? "healthy" : "managed_registration_missing"
                        : present ? "external_registration" : "available");
                if (!listed.succeeded()) result.put("diagnostic", diagnostic(listed));
            }
            results.add(result);
        }
        return List.copyOf(results);
    }

    public IntegrationRegistry registry() {
        return registry;
    }

    private Map<String, Object> setup(HostDefinition host, boolean apply)
            throws IOException, InterruptedException {
        boolean available = runner.available(host.executable());
        Map<String, Object> result = base(host, available);
        result.put("changed", false);
        if (!available) {
            result.put("state", "unavailable");
            return result;
        }

        Map<String, IntegrationRegistry.Entry> owned = registry.entries();
        CommandRunner.Result listed = runner.run(host.listCommand());
        if (!listed.succeeded()) {
            result.put("state", listed.timedOut() ? "inspection_timed_out" : "inspection_failed");
            result.put("diagnostic", diagnostic(listed));
            return result;
        }
        boolean present = containsRegistration(listed.output());
        if (present && owned.containsKey(host.id())) {
            result.put("state", "already_configured");
            return result;
        }
        if (present) {
            result.put("state", "conflict_external_registration");
            result.put("message", "A carbongate MCP server already exists but is not owned by this registry; no changes were made");
            return result;
        }
        List<String> add = host.addCommand(invocation);
        result.put("command", add);
        if (!apply) {
            result.put("state", "planned");
            return result;
        }
        CommandRunner.Result added = runner.run(add);
        if (!added.succeeded()) {
            result.put("state", added.timedOut() ? "add_timed_out" : "add_failed");
            result.put("diagnostic", diagnostic(added));
            return result;
        }
        CommandRunner.Result verified = runner.run(host.listCommand());
        if (!verified.succeeded() || !containsRegistration(verified.output())) {
            CommandRunner.Result rollback = runner.run(host.removeCommand());
            result.put("state", rollback.succeeded() ? "verification_failed_rolled_back"
                    : "verification_failed_rollback_failed");
            result.put("diagnostic", diagnostic(verified));
            return result;
        }
        registry.put(IntegrationRegistry.Entry.installed(host, invocation));
        result.put("state", "configured");
        result.put("changed", true);
        return result;
    }

    private List<HostDefinition> select(List<String> requested) {
        if (requested == null || requested.isEmpty()) {
            return catalog.stream().filter(host -> runner.available(host.executable())).toList();
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String value : requested) {
            for (String id : value.split(",")) if (!id.isBlank()) unique.add(id.trim().toLowerCase(Locale.ROOT));
        }
        return unique.stream().map(this::require).toList();
    }

    private HostDefinition require(String id) {
        return catalog.stream().filter(host -> host.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown integration host: " + id));
    }

    private boolean containsRegistration(String output) {
        return output != null && output.toLowerCase(Locale.ROOT).contains(HostDefinition.SERVER_NAME);
    }

    private Map<String, Object> base(HostDefinition host, boolean available) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("host", host.id());
        result.put("name", host.displayName());
        result.put("available", available);
        result.put("coverage", host.coverage().name().toLowerCase(Locale.ROOT));
        result.put("coverageDescription", host.coverage().description());
        return result;
    }

    private String diagnostic(CommandRunner.Result result) {
        String value = result.timedOut() ? "command timed out" : result.output();
        value = secrets.scan(value == null ? "" : value).redacted().replace('\n', ' ').replace('\r', ' ').trim();
        if (value.isBlank()) value = "exit code " + result.exitCode();
        return value.length() <= 512 ? value : value.substring(0, 512) + "…";
    }
}
