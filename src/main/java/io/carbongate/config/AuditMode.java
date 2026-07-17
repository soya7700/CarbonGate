package io.carbongate.config;

import java.util.Locale;

public enum AuditMode {
    LOCAL_MINIMAL,
    ENTERPRISE_DETAILED;

    public static AuditMode parse(String value) {
        if (value == null || value.isBlank()) return LOCAL_MINIMAL;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return LOCAL_MINIMAL;
        }
    }
}
