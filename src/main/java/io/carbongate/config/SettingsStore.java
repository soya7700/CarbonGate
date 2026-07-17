package io.carbongate.config;

import io.carbongate.audit.SecurityEventLog;
import io.carbongate.policy.EnforcementMode;
import io.carbongate.policy.RuleConfiguration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SettingsStore {
    public static final String MODE = "mode";
    public static final String RULE_SHELL = "rules.shell.enabled";
    public static final String RULE_FILESYSTEM = "rules.filesystem.enabled";
    public static final String RULE_NETWORK = "rules.network.enabled";
    public static final String RULE_SECRETS = "rules.secrets.enabled";
    public static final String AUDIT_MODE = "audit.mode";
    public static final String LOCAL_LOG_LIMIT = "audit.local.dailyLimitBytes";
    public static final String ENTERPRISE_DIRECTORY = "audit.enterprise.directory";
    public static final String ENTERPRISE_LIMIT = "audit.enterprise.dailyLimitBytes";
    public static final String CONSOLE_WARNING_LIMIT = "alerts.consoleDailyLimit";

    private final Path home;
    private final Path path;

    public SettingsStore(Path home) {
        this.home = home.toAbsolutePath().normalize();
        this.path = this.home.resolve("carbon.conf");
    }

    public EnforcementMode mode() {
        return EnforcementMode.parseStored(values().get(MODE));
    }

    public RuleConfiguration rules() {
        Map<String, String> values = values();
        return new RuleConfiguration(bool(values, RULE_SHELL), bool(values, RULE_FILESYSTEM),
                bool(values, RULE_NETWORK), bool(values, RULE_SECRETS));
    }

    public AuditMode auditMode() {
        return AuditMode.parse(values().get(AUDIT_MODE));
    }

    public long localDailyLimitBytes() {
        long configured = number(values().get(LOCAL_LOG_LIMIT), SecurityEventLog.DEFAULT_DAILY_LIMIT_BYTES);
        return Math.max(1L, Math.min(configured, SecurityEventLog.DEFAULT_DAILY_LIMIT_BYTES));
    }

    public long enterpriseDailyLimitBytes() {
        return Math.max(1L, number(values().get(ENTERPRISE_LIMIT), 100_000_000L));
    }

    public Path enterpriseDirectory() {
        Path configured = Path.of(values().get(ENTERPRISE_DIRECTORY));
        return (configured.isAbsolute() ? configured : home.resolve(configured)).toAbsolutePath().normalize();
    }

    public int consoleDailyWarningLimit() {
        return (int) Math.max(0L, Math.min(number(values().get(CONSOLE_WARNING_LIMIT), 100L), 10_000L));
    }

    public synchronized boolean initialize() throws IOException {
        if (Files.isRegularFile(path)) return false;
        write(defaults());
        return true;
    }

    public synchronized void setMode(EnforcementMode mode) throws IOException {
        set(MODE, mode.name());
    }

    public synchronized void set(String key, String value) throws IOException {
        validate(key, value);
        Map<String, String> updated = values();
        updated.put(key, normalize(key, value));
        write(updated);
    }

    public Map<String, String> snapshot() {
        return Map.copyOf(values());
    }

    public Path path() {
        return path;
    }

    public Path home() {
        return home;
    }

    private Map<String, String> values() {
        Map<String, String> result = defaults();
        if (!Files.isRegularFile(path)) return result;
        try {
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                int separator = trimmed.indexOf('=');
                if (separator <= 0) continue;
                String key = trimmed.substring(0, separator).trim();
                String value = trimmed.substring(separator + 1).trim();
                if (result.containsKey(key)) {
                    try {
                        validate(key, value);
                        result.put(key, normalize(key, value));
                    } catch (IllegalArgumentException ignored) {
                        // Fail safely to the default for malformed configuration.
                    }
                }
            }
        } catch (IOException ignored) {
            return defaults();
        }
        return result;
    }

    private Map<String, String> defaults() {
        Map<String, String> result = new LinkedHashMap<>();
        result.put(MODE, EnforcementMode.BALANCED.name());
        result.put(RULE_SHELL, "true");
        result.put(RULE_FILESYSTEM, "true");
        result.put(RULE_NETWORK, "true");
        result.put(RULE_SECRETS, "true");
        result.put(AUDIT_MODE, AuditMode.LOCAL_MINIMAL.name());
        result.put(LOCAL_LOG_LIMIT, String.valueOf(SecurityEventLog.DEFAULT_DAILY_LIMIT_BYTES));
        result.put(ENTERPRISE_DIRECTORY, "enterprise-audit");
        result.put(ENTERPRISE_LIMIT, "100000000");
        result.put(CONSOLE_WARNING_LIMIT, "100");
        return result;
    }

    private void validate(String key, String value) {
        if (!defaults().containsKey(key)) throw new IllegalArgumentException("Unknown configuration key: " + key);
        if (value == null || value.contains("\n") || value.contains("\r")) {
            throw new IllegalArgumentException("Invalid configuration value for " + key);
        }
        switch (key) {
            case MODE -> EnforcementMode.valueOf(value.trim().toUpperCase());
            case RULE_SHELL, RULE_FILESYSTEM, RULE_NETWORK, RULE_SECRETS -> {
                if (!List.of("true", "false").contains(value.trim().toLowerCase())) {
                    throw new IllegalArgumentException(key + " must be true or false");
                }
            }
            case AUDIT_MODE -> AuditMode.valueOf(value.trim().toUpperCase());
            case LOCAL_LOG_LIMIT -> {
                long limit = Long.parseLong(value.trim());
                if (limit < 1 || limit > SecurityEventLog.DEFAULT_DAILY_LIMIT_BYTES) {
                    throw new IllegalArgumentException(key + " must be between 1 and 1000000");
                }
            }
            case ENTERPRISE_LIMIT -> {
                if (Long.parseLong(value.trim()) < 1) throw new IllegalArgumentException(key + " must be positive");
            }
            case CONSOLE_WARNING_LIMIT -> {
                long limit = Long.parseLong(value.trim());
                if (limit < 0 || limit > 10_000) {
                    throw new IllegalArgumentException(key + " must be between 0 and 10000");
                }
            }
            case ENTERPRISE_DIRECTORY -> {
                if (value.isBlank()) throw new IllegalArgumentException(key + " cannot be empty");
            }
            default -> throw new IllegalArgumentException("Unknown configuration key: " + key);
        }
    }

    private String normalize(String key, String value) {
        return switch (key) {
            case MODE, AUDIT_MODE -> value.trim().toUpperCase();
            case RULE_SHELL, RULE_FILESYSTEM, RULE_NETWORK, RULE_SECRETS -> value.trim().toLowerCase();
            default -> value.trim();
        };
    }

    private boolean bool(Map<String, String> values, String key) {
        return Boolean.parseBoolean(values.get(key));
    }

    private long number(String value, long fallback) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private void write(Map<String, String> values) throws IOException {
        Files.createDirectories(home);
        StringBuilder content = new StringBuilder("# CarbonGate configuration (UTF-8)\n");
        values.forEach((key, value) -> content.append(key).append('=').append(value).append('\n'));
        Path temporary = home.resolve("carbon.conf.tmp");
        Files.writeString(temporary, content, StandardCharsets.UTF_8);
        try {
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
