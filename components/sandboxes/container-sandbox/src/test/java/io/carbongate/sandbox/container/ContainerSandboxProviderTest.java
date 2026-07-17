package io.carbongate.sandbox.container;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class ContainerSandboxProviderTest {
    private static final String IMAGE = "example.com/carbongate/tool@sha256:" + "a".repeat(64);

    public static void main(String[] args) throws Exception {
        Path workspace = Files.createTempDirectory("carbon-sandbox-workspace-");
        var request = new ContainerSandboxProvider.SandboxRequest(workspace, IMAGE,
                List.of("java", "--version"), false, 1000, 256, 1.0);
        List<String> command = ContainerSandboxProvider.command("docker", request, "carbongate-test");
        assert command.contains("--network=none");
        assert command.contains("--read-only");
        assert command.contains("--cap-drop=ALL");
        assert command.contains("--security-opt=no-new-privileges");
        assert command.contains("--pull=never");
        assert command.contains("--name=carbongate-test");
        assert command.stream().anyMatch(value -> value.startsWith("--mount=") && value.endsWith(",readonly"));
        assert command.indexOf(IMAGE) < command.indexOf("java");

        try {
            new ContainerSandboxProvider.SandboxRequest(workspace, "example.com/tool:latest",
                    List.of("sh"), false, 1000, 256, 1.0);
            throw new AssertionError("mutable image tags must be rejected");
        } catch (IllegalArgumentException expected) {
            assert expected.getMessage().contains("sha256");
        }
        System.out.println("Container Sandbox Provider tests passed.");
    }
}
