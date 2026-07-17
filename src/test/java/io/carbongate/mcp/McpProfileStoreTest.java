package io.carbongate.mcp;

import io.carbongate.json.Json;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class McpProfileStoreTest {
    public static void run() throws Exception {
        Path home = Files.createTempDirectory("carbon-mcp-profiles-");
        Path workspace = Files.createDirectory(home.resolve("workspace"));
        McpProfileStore store = new McpProfileStore(home);

        McpProfileStore.Profile created = store.put("Git_Service", workspace,
                List.of("example-mcp", "--stdio"), false);
        assert created.name().equals("git_service");
        assert created.workspace().equals(workspace.toAbsolutePath().normalize());
        assert store.list().size() == 1;
        assert store.require("git_service").command().equals(List.of("example-mcp", "--stdio"));
        assert Files.size(store.path()) < 4096 : "compact profile state should remain small";
        assert Json.object(Files.readString(store.path())).get("version").equals(1L);

        expectRejected(() -> store.put("git_service", workspace, List.of("other-mcp"), false),
                "already exists");
        McpProfileStore.Profile replaced = store.put("git_service", workspace,
                List.of("other-mcp"), true);
        assert replaced.createdAt().equals(created.createdAt());
        assert replaced.command().equals(List.of("other-mcp"));

        expectRejected(() -> store.put("bad name", workspace, List.of("example-mcp"), false),
                "must match");
        expectRejected(() -> store.put("missing", home.resolve("missing"), List.of("example-mcp"), false),
                "existing directory");
        expectRejected(() -> store.put("credential", workspace,
                List.of("example-mcp", "--token", "synthetic-credential-value"), false), "credential option");
        expectRejected(() -> store.put("secret", workspace,
                List.of("example-mcp", "password=synthetic-secret-value"), false), "secret-like");
        expectRejected(() -> store.put("header", workspace,
                List.of("example-mcp", "--header", "Authorization:", "Bearer", "abcdefghijklmnop"), false),
                "secret-like");
        String persisted = Files.readString(store.path());
        assert !persisted.contains("synthetic-credential-value");
        assert !persisted.contains("synthetic-secret-value");

        assert store.remove("git_service");
        assert !store.remove("git_service");
        assert store.list().isEmpty();

        Files.writeString(store.path(), Json.stringify(java.util.Map.of("version", 1, "profiles", List.of(
                java.util.Map.of("name", "tampered", "workspace", workspace.toString(),
                        "command", List.of("example-mcp", "password=synthetic-tampered-secret"),
                        "createdAt", "now", "updatedAt", "now")))));
        expectRejected(store::list, "secret-like");
    }

    private static void expectRejected(ThrowingAction action, String message) throws Exception {
        try {
            action.run();
            throw new AssertionError("profile input should have been rejected");
        } catch (IllegalArgumentException expected) {
            assert expected.getMessage().contains(message) : expected.getMessage();
            assert !expected.getMessage().contains("synthetic-secret-value");
            assert !expected.getMessage().contains("synthetic-credential-value");
        }
    }

    @FunctionalInterface
    private interface ThrowingAction {
        void run() throws Exception;
    }
}
