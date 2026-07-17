package io.carbongate.integration;

import java.util.List;
import java.util.Locale;

import io.carbongate.integration.HostDefinition.RegistrationStyle;

public final class HostCatalog {
    private static final List<HostDefinition> HOSTS = List.of(
            host("codex", "OpenAI Codex CLI", "codex", RegistrationStyle.DOUBLE_DASH),
            host("claude", "Claude Code", "claude", RegistrationStyle.CLAUDE),
            host("openclaw", "OpenClaw", "openclaw", RegistrationStyle.OPENCLAW),
            host("qoder", "Qoder CLI", "qodercli", RegistrationStyle.QODER),
            host("codebuddy", "CodeBuddy / WorkBuddy CLI", "codebuddy", RegistrationStyle.CODEBUDDY),
            host("gemini", "Gemini CLI", "gemini", RegistrationStyle.GEMINI),
            host("copilot", "GitHub Copilot CLI", "copilot", RegistrationStyle.DOUBLE_DASH)
    );

    private HostCatalog() {}

    public static List<HostDefinition> all() {
        return HOSTS;
    }

    public static HostDefinition require(String id) {
        String normalized = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        return HOSTS.stream().filter(host -> host.id().equals(normalized)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown integration host: " + id));
    }

    private static HostDefinition host(String id, String name, String executable, RegistrationStyle style) {
        return new HostDefinition(id, name, executable, style, Coverage.CONTROL_ONLY);
    }
}
