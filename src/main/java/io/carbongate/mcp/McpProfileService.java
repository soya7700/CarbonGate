package io.carbongate.mcp;

import io.carbongate.integration.Coverage;
import io.carbongate.integration.IntegrationInvocation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class McpProfileService {
    private static final List<String> FORMATS = List.of("descriptor", "mcp-json", "codex-toml");
    private final McpProfileStore store;

    public McpProfileService(McpProfileStore store) {
        this.store = store;
    }

    public Map<String, Object> export(String name, String requestedFormat) throws java.io.IOException {
        McpProfileStore.Profile profile = store.require(name);
        String format = requestedFormat == null || requestedFormat.isBlank()
                ? "descriptor" : requestedFormat.trim().toLowerCase(Locale.ROOT);
        if (!FORMATS.contains(format)) {
            throw new IllegalArgumentException("Unsupported MCP profile export format: " + format
                    + "; allowed: " + String.join(", ", FORMATS));
        }
        String serverName = "carbongate-" + profile.name();
        List<String> invocation = IntegrationInvocation.forArguments("mcp", "profile", "run", profile.name());
        Map<String, Object> descriptor = Map.of("command", invocation.getFirst(),
                "args", invocation.stream().skip(1).toList());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", profile.name());
        result.put("serverName", serverName);
        result.put("format", format);
        result.put("transport", "stdio");
        result.put("coverage", Coverage.MCP_ONLY.name().toLowerCase(Locale.ROOT));
        result.put("coverageDescription", Coverage.MCP_ONLY.description());
        result.put("content", switch (format) {
            case "descriptor" -> descriptor;
            case "mcp-json" -> Map.of("mcpServers", Map.of(serverName, descriptor));
            case "codex-toml" -> codexToml(serverName, invocation);
            default -> throw new IllegalStateException("Unexpected format: " + format);
        });
        return result;
    }

    private String codexToml(String serverName, List<String> invocation) {
        List<String> args = invocation.stream().skip(1).map(this::tomlString).toList();
        return "[mcp_servers." + serverName + "]\ncommand = " + tomlString(invocation.getFirst())
                + "\nargs = [" + String.join(", ", args) + "]\n";
    }

    private String tomlString(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
