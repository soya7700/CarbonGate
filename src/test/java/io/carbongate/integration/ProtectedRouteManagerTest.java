package io.carbongate.integration;

import io.carbongate.mcp.McpProfileStore;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ProtectedRouteManagerTest {
    public static void run() throws Exception {
        Path home = Files.createTempDirectory("carbon-protected-routes-");
        Path workspace = Files.createDirectory(home.resolve("workspace"));
        FakeRunner runner = new FakeRunner();
        ProtectedRouteManager manager = new ProtectedRouteManager(home, runner);

        var dryRun = manager.protect("generic", "preview", workspace, List.of("example-mcp"), true);
        assert dryRun.get("state").equals("planned");
        assert manager.list().isEmpty();
        assert new McpProfileStore(home).list().isEmpty() : "dry-run must not write profile state";

        var generic = manager.protect("generic", "files", workspace, List.of("example-mcp", "--stdio"), false);
        assert generic.get("state").equals("protected");
        assert generic.get("coverage").equals("mcp_only");
        assert manager.list().size() == 1;
        assert manager.unprotect("generic", "files").get("state").equals("removed");
        assert new McpProfileStore(home).list().isEmpty();

        var codex = manager.protect("codex", "source", workspace, List.of("source-mcp"), false);
        assert codex.get("state").equals("protected");
        assert runner.present.contains("carbongate-source");
        assert manager.protect("codex", "source", workspace, List.of("source-mcp"), false)
                .get("state").equals("already_protected");
        assert manager.unprotect("codex", "source").get("state").equals("removed");

        Path conflictHome = Files.createTempDirectory("carbon-protected-conflict-");
        FakeRunner conflictRunner = new FakeRunner();
        conflictRunner.present.add("carbongate-external");
        var conflict = new ProtectedRouteManager(conflictHome, conflictRunner)
                .protect("codex", "external", workspace, List.of("external-mcp"), false);
        assert conflict.get("state").equals("conflict_external_registration");
        assert new McpProfileStore(conflictHome).list().isEmpty();

        Path rollbackHome = Files.createTempDirectory("carbon-protected-rollback-");
        FakeRunner rollbackRunner = new FakeRunner();
        rollbackRunner.failVerification = true;
        var rollback = new ProtectedRouteManager(rollbackHome, rollbackRunner)
                .protect("codex", "rollback", workspace, List.of("rollback-mcp"), false);
        assert rollback.get("state").equals("verification_failed_rolled_back");
        assert new McpProfileStore(rollbackHome).list().isEmpty();
    }

    private static final class FakeRunner implements CommandRunner {
        private final Set<String> present = new HashSet<>();
        private boolean failVerification;

        @Override
        public boolean available(String executable) {
            return executable.equals("codex");
        }

        @Override
        public Result run(List<String> command) {
            if (command.equals(List.of("codex", "mcp", "list"))) {
                String output = failVerification ? "No MCP servers" : String.join("\n", present);
                return new Result(0, output, false);
            }
            if (command.size() >= 5 && command.subList(0, 3).equals(List.of("codex", "mcp", "add"))) {
                String name = command.get(3);
                if (!failVerification) present.add(name);
                return new Result(0, "added", false);
            }
            if (command.size() == 4 && command.subList(0, 3).equals(List.of("codex", "mcp", "remove"))) {
                present.remove(command.get(3));
                return new Result(0, "removed", false);
            }
            return new Result(1, "unexpected", false);
        }
    }
}
