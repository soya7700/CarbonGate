package io.carbongate.integration;

import java.util.ArrayList;
import java.util.List;

public record HostDefinition(String id, String displayName, String executable,
                             RegistrationStyle style, Coverage coverage) {
    public static final String SERVER_NAME = "carbongate";

    public List<String> listCommand() {
        return List.of(executable, "mcp", "list");
    }

    public List<String> addCommand(List<String> invocation) {
        ArrayList<String> command = new ArrayList<>();
        switch (style) {
            case DOUBLE_DASH -> {
                command.addAll(List.of(executable, "mcp", "add", SERVER_NAME, "--"));
                command.addAll(invocation);
            }
            case CLAUDE -> {
                command.addAll(List.of(executable, "mcp", "add", "--transport", "stdio",
                        "--scope", "user", SERVER_NAME, "--"));
                command.addAll(invocation);
            }
            case QODER -> {
                command.addAll(List.of(executable, "mcp", "add", "-s", "user", SERVER_NAME, "--"));
                command.addAll(invocation);
            }
            case OPENCLAW -> {
                command.addAll(List.of(executable, "mcp", "add", SERVER_NAME,
                        "--command", invocation.getFirst()));
                invocation.stream().skip(1).forEach(argument -> {
                    command.add("--arg");
                    command.add(argument);
                });
            }
            case CODEBUDDY -> {
                command.addAll(List.of(executable, "mcp", "add", SERVER_NAME));
                command.addAll(invocation);
            }
            case GEMINI -> {
                command.addAll(List.of(executable, "mcp", "add", "--scope", "user", SERVER_NAME));
                command.addAll(invocation);
            }
        }
        return List.copyOf(command);
    }

    public List<String> removeCommand() {
        return switch (style) {
            case OPENCLAW -> List.of(executable, "mcp", "unset", SERVER_NAME);
            case GEMINI -> List.of(executable, "mcp", "remove", "--scope", "user", SERVER_NAME);
            default -> List.of(executable, "mcp", "remove", SERVER_NAME);
        };
    }

    public enum RegistrationStyle {
        DOUBLE_DASH,
        CLAUDE,
        QODER,
        OPENCLAW,
        CODEBUDDY,
        GEMINI
    }
}
