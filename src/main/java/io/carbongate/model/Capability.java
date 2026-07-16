package io.carbongate.model;

public enum Capability {
    SHELL,
    FILESYSTEM,
    NETWORK,
    SECRET,
    UNKNOWN;

    public static Capability parse(String value) {
        if (value == null) return UNKNOWN;
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return UNKNOWN;
        }
    }
}
