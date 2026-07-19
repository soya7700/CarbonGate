package io.carbongate.integration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class IntegrationManagerTest {
    public static void run() throws Exception {
        HostDefinition codex = HostCatalog.require("codex");
        List<String> invocation = List.of("/opt/carbongate/carbon", "mcp", "serve");

        Path home = Files.createTempDirectory("carbon-integrations-");
        FakeRunner runner = new FakeRunner("codex");
        IntegrationManager manager = new IntegrationManager(new IntegrationRegistry(home), runner,
                List.of(codex), invocation);

        var configured = manager.setup(List.of("codex", "codex"), true);
        assert configured.size() == 1 : "duplicate host selection must be collapsed";
        assert configured.getFirst().get("state").equals("configured");
        assert runner.adds == 1;
        assert manager.registry().entries().containsKey("codex");

        var second = manager.setup(List.of("codex"), true);
        assert second.getFirst().get("state").equals("already_configured");
        assert runner.adds == 1 : "idempotent setup must not add a duplicate";
        assert manager.doctor().getFirst().get("state").equals("healthy");

        var removed = manager.remove("codex");
        assert removed.get("state").equals("removed");
        assert runner.removes == 1;
        assert manager.registry().entries().isEmpty();

        Path conflictHome = Files.createTempDirectory("carbon-integrations-conflict-");
        FakeRunner conflict = new FakeRunner("codex");
        conflict.present = true;
        IntegrationManager conflictManager = new IntegrationManager(new IntegrationRegistry(conflictHome), conflict,
                List.of(codex), invocation);
        var conflictResult = conflictManager.setup(List.of("codex"), true).getFirst();
        assert conflictResult.get("state").equals("conflict_external_registration");
        assert conflict.adds == 0 : "external registrations must never be overwritten";

        Path rollbackHome = Files.createTempDirectory("carbon-integrations-rollback-");
        FakeRunner rollback = new FakeRunner("codex");
        rollback.failVerification = true;
        IntegrationManager rollbackManager = new IntegrationManager(new IntegrationRegistry(rollbackHome), rollback,
                List.of(codex), invocation);
        var rollbackResult = rollbackManager.setup(List.of("codex"), true).getFirst();
        assert rollbackResult.get("state").equals("verification_failed_rolled_back");
        assert rollback.adds == 1;
        assert rollback.removes == 1;
        assert rollbackManager.registry().entries().isEmpty();

        assert codex.addCommand(invocation).equals(List.of("codex", "mcp", "add", "carbongate", "--",
                "/opt/carbongate/carbon", "mcp", "serve"));
        assert HostCatalog.require("claude").addCommand(invocation).containsAll(List.of("--transport", "stdio", "--scope", "user"));
        assert HostCatalog.require("openclaw").addCommand(invocation).containsAll(List.of("--command", "/opt/carbongate/carbon", "--arg", "serve"));
        assert HostCatalog.require("qoder").addCommand(invocation).containsAll(List.of("-s", "user"));
        assert HostCatalog.require("gemini").addCommand(invocation).containsAll(List.of("--scope", "user"));
        assert HostCatalog.all().stream().allMatch(host -> host.coverage() == Coverage.CONTROL_ONLY);

        Path desktopCodex = Files.createTempFile("carbon-codex-desktop-", ".bin");
        assert desktopCodex.toFile().setExecutable(true);
        SystemCommandRunner desktopRunner = new SystemCommandRunner(Duration.ofSeconds(1),
                Map.of("PATH", "", "CODEX_CLI_PATH", desktopCodex.toString()));
        assert desktopRunner.available("codex");
        assert desktopRunner.resolveExecutable("codex").equals(desktopCodex);

        Path nativeCarbon = Files.createTempFile("carbon-native-invocation-", ".bin");
        assert IntegrationInvocation.nativeInvocation(nativeCarbon, List.of("mcp", "serve"))
                .equals(List.of(nativeCarbon.toAbsolutePath().normalize().toString(), "mcp", "serve"));
    }

    private static final class FakeRunner implements CommandRunner {
        private final Set<String> executables = new HashSet<>();
        private final List<List<String>> commands = new ArrayList<>();
        private boolean present;
        private boolean failVerification;
        private int adds;
        private int removes;

        private FakeRunner(String... available) {
            executables.addAll(List.of(available));
        }

        @Override
        public boolean available(String executable) {
            return executables.contains(executable);
        }

        @Override
        public Result run(List<String> command) {
            commands.add(List.copyOf(command));
            if (command.size() >= 3 && command.get(1).equals("mcp") && command.get(2).equals("list")) {
                return new Result(0, present ? "carbongate configured" : "No MCP servers", false);
            }
            if (command.contains("add")) {
                adds++;
                if (!failVerification) present = true;
                return new Result(0, "added", false);
            }
            if (command.contains("remove") || command.contains("unset")) {
                removes++;
                present = false;
                return new Result(0, "removed", false);
            }
            return new Result(1, "unexpected", false);
        }
    }
}
