package io.carbongate.integration;

import io.carbongate.json.Json;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class IntegrationGuideServiceTest {
    public static void run() {
        List<String> invocation = List.of("C:\\Program Files\\CarbonGate\\java.exe", "-jar",
                "C:\\Program Files\\CarbonGate\\carbongate.jar", "mcp", "serve");
        IntegrationGuideService guides = new IntegrationGuideService(invocation);

        var catalog = guides.catalog();
        Set<String> ids = new HashSet<>();
        for (Map<String, Object> target : catalog) {
            assert ids.add(String.valueOf(target.get("host"))) : "integration target ids must be unique";
        }
        assert ids.containsAll(List.of("codex", "claude", "openclaw", "qoder", "codebuddy",
                "gemini", "copilot", "generic-stdio", "workbuddy-desktop", "coze"));

        var automatic = guides.guide("codex");
        assert automatic.get("setupMethod").equals("automatic_cli");
        assert Json.stringify(automatic).contains("setupCommand");
        assert Json.stringify(automatic).contains("setup");

        var generic = guides.export("generic-stdio", "descriptor");
        assert generic.get("supported").equals(true);
        assert Json.stringify(generic).contains("carbongate.jar");

        var mcpJson = guides.export("workbuddy-desktop", "mcp-json");
        assert Json.stringify(mcpJson).contains("mcpServers");
        assert Json.stringify(mcpJson).contains("carbongate");

        var toml = guides.export("generic-stdio", "codex-toml");
        String content = String.valueOf(toml.get("content"));
        assert content.contains("[mcp_servers.carbongate]");
        assert content.contains("C:\\\\Program Files");

        var coze = guides.export("coze", "descriptor");
        assert coze.get("supported").equals(false);
        assert coze.get("remoteTransportRequired").equals(true);
        assert !coze.containsKey("content");

        try {
            guides.guide("missing-host");
            throw new AssertionError("unknown guided hosts must be rejected");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }
}
