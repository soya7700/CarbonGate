package io.carbongate.policy;

public record RuleConfiguration(boolean shell, boolean filesystem, boolean network, boolean secrets) {
    public static RuleConfiguration allEnabled() {
        return new RuleConfiguration(true, true, true, true);
    }
}
