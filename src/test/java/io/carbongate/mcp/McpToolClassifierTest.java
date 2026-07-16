package io.carbongate.mcp;

import io.carbongate.model.Capability;

import java.nio.file.Path;
import java.util.Map;

public final class McpToolClassifierTest {
    public static void run() {
        McpToolClassifier classifier = new McpToolClassifier();
        Path workspace = Path.of(".").toAbsolutePath().normalize();

        var shell = classifier.classify(Map.of(
                "method", "tools/call",
                "params", Map.of("name", "execute_command", "arguments", Map.of("command", "rm -rf /"))
        ), workspace);
        assert shell.capability() == Capability.SHELL;
        assert shell.resource().equals("rm -rf /");

        var move = classifier.classify(Map.of(
                "method", "tools/call",
                "params", Map.of("name", "move_file", "arguments", Map.of(
                        "source", "safe.txt", "target", "../outside.txt"))
        ), workspace);
        assert move.capability() == Capability.FILESYSTEM;
        assert move.resource().equals("../outside.txt");

        assert classifier.classify(Map.of("method", "tools/list"), workspace) == null;
    }
}
