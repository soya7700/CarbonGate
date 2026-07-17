package io.carbongate.mcp;

import io.carbongate.config.SettingsStore;
import io.carbongate.json.Json;
import io.carbongate.policy.EnforcementMode;
import io.carbongate.policy.PolicyProfile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class McpControlServerTest {
    public static void run() throws Exception {
        Path home = Files.createTempDirectory("carbon-mcp-control-");
        McpControlServer server = new McpControlServer(home, PolicyProfile.BALANCED);

        Map<String, Object> initialized = server.handle(Map.of(
                "jsonrpc", "2.0", "id", 1L, "method", "initialize", "params", Map.of()));
        assert initialized.get("id").equals(1L);
        assert Json.stringify(initialized).contains("carbongate-control");

        Map<String, Object> parsedInitialize = server.handle(Json.object(
                "{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"initialize\",\"params\":{}}"));
        assert parsedInitialize.get("id").equals(7L) : "integer JSON-RPC ids must remain integers";

        Map<String, Object> tools = server.handle(Map.of(
                "jsonrpc", "2.0", "id", 2L, "method", "tools/list", "params", Map.of()));
        String toolsJson = Json.stringify(tools);
        assert toolsJson.contains("carbon_status");
        assert toolsJson.contains("carbon_rules");
        assert toolsJson.contains("carbon_set_mode");
        assert toolsJson.contains("carbon_approve");
        assert toolsJson.contains("carbon_integration_guide");
        assert toolsJson.contains("carbon_mcp_profiles");
        assert toolsJson.contains("carbon_mcp_profile_export");
        assert toolsJson.contains("carbon_doctor");

        Map<String, Object> status = call(server, 3, "carbon_status", Map.of());
        assert Json.stringify(status).contains("dailyLogByteLimit");
        assert Json.stringify(status).contains("10000000");

        Map<String, Object> mode = call(server, 4, "carbon_set_mode",
                Map.of("instruction", "以后每次操作都需要手动授权"));
        assert Json.stringify(mode).contains("approval");
        assert new SettingsStore(home).mode() == EnforcementMode.APPROVAL;

        Map<String, Object> rules = call(server, 5, "carbon_rules", Map.of());
        assert Json.stringify(rules).contains("shellRules");

        Map<String, Object> guide = call(server, 51, "carbon_integration_guide",
                Map.of("host", "workbuddy-desktop"));
        assert Json.stringify(guide).contains("guided");
        Map<String, Object> coze = call(server, 52, "carbon_integration_export",
                Map.of("host", "coze"));
        assert Json.stringify(coze).contains("remoteTransportRequired");

        Path workspace = Files.createDirectory(home.resolve("workspace"));
        new McpProfileStore(home).put("example", workspace, java.util.List.of("example-mcp"), false);
        Map<String, Object> profiles = call(server, 53, "carbon_mcp_profiles", Map.of());
        assert Json.stringify(profiles).contains("example");
        Map<String, Object> profile = call(server, 54, "carbon_mcp_profile", Map.of("name", "example"));
        assert Json.stringify(profile).contains("example-mcp");
        Map<String, Object> exported = call(server, 55, "carbon_mcp_profile_export",
                Map.of("name", "example", "format", "mcp-json"));
        assert Json.stringify(exported).contains("mcp_only");

        Map<String, Object> invalid = server.handle(Map.of(
                "jsonrpc", "2.0", "id", 6L, "method", "tools/call",
                "params", Map.of("name", "not_a_tool", "arguments", Map.of())));
        assert Json.stringify(invalid).contains("-32602");

        assert server.handle(Map.of("jsonrpc", "2.0", "method", "notifications/initialized")) == null;
    }

    private static Map<String, Object> call(McpControlServer server, long id, String name,
                                             Map<String, Object> arguments) throws Exception {
        return server.handle(Map.of("jsonrpc", "2.0", "id", id, "method", "tools/call",
                "params", Map.of("name", name, "arguments", arguments)));
    }
}
