package io.carbongate.integration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class IntegrationGuideService {
    private static final List<GuidedTarget> GUIDED = List.of(
            new GuidedTarget("generic-stdio", "Generic local stdio MCP host", false,
                    "Add a local stdio MCP server named carbongate using the exported command and arguments.",
                    List.of("descriptor", "mcp-json", "codex-toml")),
            new GuidedTarget("workbuddy-desktop", "WorkBuddy desktop", false,
                    "Open MCP settings, choose Add Server, select local stdio, and enter the exported carbongate command and arguments.",
                    List.of("descriptor", "mcp-json")),
            new GuidedTarget("coze", "Coze / 扣子 cloud", true,
                    "CarbonGate currently exposes a local stdio control server. Coze cloud requires a remotely reachable authenticated transport, which is not implemented yet. Do not expose the loopback HTTP gateway to the Internet.",
                    List.of())
    );

    private final List<String> invocation;

    public IntegrationGuideService(List<String> invocation) {
        this.invocation = List.copyOf(invocation);
        if (this.invocation.isEmpty()) throw new IllegalArgumentException("CarbonGate invocation cannot be empty");
    }

    public List<Map<String, Object>> catalog() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (HostDefinition host : HostCatalog.all()) {
            Map<String, Object> value = base(host.id(), host.displayName(), host.coverage());
            value.put("setupMethod", "automatic_cli");
            value.put("setupCommand", List.of("carbon", "setup", "--host", host.id()));
            value.put("transport", "stdio");
            result.add(value);
        }
        for (GuidedTarget target : GUIDED) result.add(describe(target));
        return List.copyOf(result);
    }

    public Map<String, Object> guide(String requestedId) {
        String id = normalize(requestedId);
        for (HostDefinition host : HostCatalog.all()) {
            if (host.id().equals(id)) {
                Map<String, Object> value = base(host.id(), host.displayName(), host.coverage());
                value.put("setupMethod", "automatic_cli");
                value.put("setupCommand", List.of("carbon", "setup", "--host", host.id()));
                value.put("fallback", "Use carbon integrations export generic-stdio --format mcp-json when automatic registration is unavailable");
                value.put("transport", "stdio");
                value.put("invocation", invocationMap());
                return value;
            }
        }
        return describe(requireGuided(id));
    }

    public Map<String, Object> export(String requestedId, String requestedFormat) {
        String id = normalize(requestedId);
        GuidedTarget guided = guided(id);
        if (guided != null && guided.remoteTransportRequired()) {
            Map<String, Object> unavailable = describe(guided);
            unavailable.put("supported", false);
            unavailable.put("format", requestedFormat == null || requestedFormat.isBlank()
                    ? "descriptor" : requestedFormat.trim().toLowerCase(Locale.ROOT));
            return unavailable;
        }
        if (guided == null) HostCatalog.require(id);
        String format = requestedFormat == null || requestedFormat.isBlank()
                ? "descriptor" : requestedFormat.trim().toLowerCase(Locale.ROOT);
        List<String> allowed = guided == null
                ? List.of("descriptor", "mcp-json", "codex-toml") : guided.formats();
        if (!allowed.contains(format)) {
            throw new IllegalArgumentException("Unsupported export format for " + id + ": " + format
                    + "; allowed: " + String.join(", ", allowed));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("host", id);
        result.put("serverName", HostDefinition.SERVER_NAME);
        result.put("supported", true);
        result.put("format", format);
        result.put("transport", "stdio");
        result.put("coverage", Coverage.CONTROL_ONLY.name().toLowerCase(Locale.ROOT));
        result.put("warning", Coverage.CONTROL_ONLY.description());
        result.put("content", switch (format) {
            case "descriptor" -> invocationMap();
            case "mcp-json" -> Map.of("mcpServers", Map.of(HostDefinition.SERVER_NAME, invocationMap()));
            case "codex-toml" -> codexToml();
            default -> throw new IllegalArgumentException("Unsupported export format: " + format);
        });
        return result;
    }

    private Map<String, Object> describe(GuidedTarget target) {
        Map<String, Object> value = base(target.id(), target.displayName(), Coverage.GUIDED);
        value.put("setupMethod", "guided");
        value.put("transport", target.remoteTransportRequired() ? "remote_required" : "stdio");
        value.put("remoteTransportRequired", target.remoteTransportRequired());
        value.put("exportFormats", target.formats());
        value.put("instructions", target.instructions());
        if (!target.remoteTransportRequired()) value.put("invocation", invocationMap());
        return value;
    }

    private Map<String, Object> invocationMap() {
        return Map.of("command", invocation.getFirst(), "args", invocation.stream().skip(1).toList());
    }

    private String codexToml() {
        List<String> args = invocation.stream().skip(1).map(this::tomlString).toList();
        return "[mcp_servers." + HostDefinition.SERVER_NAME + "]\ncommand = "
                + tomlString(invocation.getFirst()) + "\nargs = [" + String.join(", ", args) + "]\n";
    }

    private String tomlString(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private Map<String, Object> base(String id, String name, Coverage coverage) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("host", id);
        value.put("name", name);
        value.put("coverage", coverage.name().toLowerCase(Locale.ROOT));
        value.put("coverageDescription", coverage.description());
        return value;
    }

    private GuidedTarget requireGuided(String id) {
        GuidedTarget target = guided(id);
        if (target == null) throw new IllegalArgumentException("Unknown integration host: " + id);
        return target;
    }

    private GuidedTarget guided(String id) {
        return GUIDED.stream().filter(target -> target.id().equals(id)).findFirst().orElse(null);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private record GuidedTarget(String id, String displayName, boolean remoteTransportRequired,
                                String instructions, List<String> formats) {
        private GuidedTarget {
            formats = List.copyOf(formats);
        }
    }
}
