package io.carbongate.security;

import io.carbongate.model.RiskLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Conservative static analysis for shell text. This is defense in depth, not a
 * replacement for an OS sandbox.
 */
public final class ShellRiskAnalyzer {
    private static final List<Rule> RULES = List.of(
            critical("recursive deletion targeting the filesystem root",
                    "(^|[;&|]\s*)rm\\s+[^;|]*(?:-[a-zA-Z]*r[a-zA-Z]*f|-{1,2}recursive[^;|]*-{1,2}force|-{1,2}force[^;|]*-{1,2}recursive)[^;|]*\\s+(?:/|~|\\$HOME)(?:\\s|$)"),
            critical("filesystem formatting command", "(^|[;&|]\s*)(mkfs(?:\\.[a-z0-9]+)?|fdisk|parted)\\b"),
            critical("raw write to a device", "\\bdd\\b[^;&|]*\\bof=/dev/"),
            critical("host shutdown or reboot", "(^|[;&|]\s*)(shutdown|reboot|halt|poweroff)\\b"),
            critical("shell fork bomb pattern", ":\\(\\)\\s*\\{[^}]*:\\|:"),
            critical("downloaded content is piped into a shell",
                    "\\b(curl|wget)\\b[^|\\n]*\\|\\s*(sudo\\s+)?(sh|bash|zsh|fish)\\b"),
            high("privilege escalation command", "(^|[;&|]\s*)(sudo|doas|su)\\b"),
            high("recursive or forced deletion", "(^|[;&|]\s*)rm\\s+[^;|]*(?:-[a-zA-Z]*[rf][a-zA-Z]*|-{1,2}(recursive|force))"),
            high("destructive Git working-tree operation", "\\bgit\\s+(reset\\s+--hard|clean\\s+[^;&|]*-[a-zA-Z]*f)\\b"),
            high("permission or ownership change", "(^|[;&|]\s*)(chmod|chown|chgrp)\\b"),
            high("write to a protected system path", "(?:>|tee\\s+)(?:>|\\s)*(?:/etc/|/usr/|/bin/|/sbin/|/System/|/Library/)"),
            high("command evaluation from dynamic text", "(^|[;&|]\s*)(eval|exec)\\s+"),
            high("possible outbound upload", "\\bcurl\\b[^;&|]*(?:--upload-file|-T\\s|-F\\s|--data-binary)"),
            medium("package installation changes the environment",
                    "(^|[;&|]\s*)(npm|pnpm|yarn|pip|pip3|gem|brew|apt|apt-get|dnf|yum)\\s+(install|add)\\b"),
            medium("shell interpreter executes dynamic command text", "(^|[;&|]\\s*)(sh|bash|zsh|fish)\\s+-c\\b"),
            medium("network download", "(^|[;&|]\\s*)(curl|wget)\\b"),
            medium("output redirection overwrites a file", "(^|[^>])>[^>]"),
            medium("multiple shell stages increase review complexity", "(&&|\\|\\||;|`|\\$\\()")
    );

    public RiskAssessment analyze(String command) {
        if (command == null || command.isBlank()) {
            return new RiskAssessment(RiskLevel.LOW, List.of("empty command"));
        }
        String normalized = command.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
        RiskLevel risk = RiskLevel.LOW;
        List<String> findings = new ArrayList<>();
        for (Rule rule : RULES) {
            if (rule.pattern.matcher(normalized).find()) {
                if (rule.risk.ordinal() > risk.ordinal()) risk = rule.risk;
                findings.add(rule.finding);
            }
        }
        if (findings.isEmpty()) findings.add("no elevated-risk shell pattern detected");
        return new RiskAssessment(risk, findings);
    }

    private static Rule critical(String finding, String regex) {
        return new Rule(RiskLevel.CRITICAL, finding, Pattern.compile(regex, Pattern.CASE_INSENSITIVE));
    }

    private static Rule high(String finding, String regex) {
        return new Rule(RiskLevel.HIGH, finding, Pattern.compile(regex, Pattern.CASE_INSENSITIVE));
    }

    private static Rule medium(String finding, String regex) {
        return new Rule(RiskLevel.MEDIUM, finding, Pattern.compile(regex, Pattern.CASE_INSENSITIVE));
    }

    private record Rule(RiskLevel risk, String finding, Pattern pattern) {}
}
