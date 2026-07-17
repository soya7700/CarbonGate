package io.carbongate.mcp;

import io.carbongate.json.Json;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class McpProfileServiceTest {
    public static void run() throws Exception {
        Path home = Files.createTempDirectory("carbon-mcp-profile-export-");
        Path workspace = Files.createDirectory(home.resolve("workspace"));
        McpProfileStore store = new McpProfileStore(home);
        store.put("source-control", workspace, List.of("example-mcp", "--stdio"), false);
        McpProfileService service = new McpProfileService(store);

        String descriptor = Json.stringify(service.export("source-control", "descriptor"));
        assert descriptor.contains("mcp_only");
        assert descriptor.contains("\"mcp\",\"profile\",\"run\",\"source-control\"");
        assert !descriptor.contains("example-mcp") : "host descriptor must invoke the protected route";

        String mcpJson = Json.stringify(service.export("source-control", "mcp-json"));
        assert mcpJson.contains("mcpServers");
        assert mcpJson.contains("carbongate-source-control");

        String toml = String.valueOf(service.export("source-control", "codex-toml").get("content"));
        assert toml.contains("[mcp_servers.carbongate-source-control]");
        assert toml.contains("profile\", \"run\", \"source-control");

        try {
            service.export("source-control", "xml");
            throw new AssertionError("unknown export formats must be rejected");
        } catch (IllegalArgumentException expected) {
            assert expected.getMessage().contains("Unsupported");
        }
    }
}
