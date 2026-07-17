package io.carbongate.policy;

import io.carbongate.model.Decision;
import io.carbongate.model.RiskLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public enum EnforcementMode {
    BALANCED("风险分级：低风险允许，中高风险授权，严重风险禁止"),
    WARN("仅告警：显示风险但不拦截"),
    APPROVAL("每次授权：所有操作都需要一次性人工批准"),
    BLOCK("全部禁止：所有 Agent 操作都被拦截");

    private final String description;

    EnforcementMode(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }

    public Decision decide(PolicyProfile profile, RiskLevel risk) {
        return switch (this) {
            case BALANCED -> profile.decide(risk);
            case WARN -> Decision.ALLOW;
            case APPROVAL -> Decision.ASK;
            case BLOCK -> Decision.DENY;
        };
    }

    public static EnforcementMode parseStored(String value) {
        if (value == null || value.isBlank()) return BALANCED;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return BALANCED;
        }
    }

    public static EnforcementMode fromNaturalLanguage(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("请说明要切换的级别：警告、每次授权、全部禁止或平衡模式");
        }
        String normalized = text.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        List<EnforcementMode> matches = new ArrayList<>();
        if (containsAny(normalized, "警告", "提醒", "观察", "warn", "observe")) matches.add(WARN);
        if (containsAny(normalized, "每次授权", "手动授权", "每次确认", "审批模式", "approval", "ask")) matches.add(APPROVAL);
        if (containsAny(normalized, "全部禁止", "完全拦截", "全部拦截", "封锁", "block", "denyall")) matches.add(BLOCK);
        if (containsAny(normalized, "平衡", "默认", "自动分级", "balanced", "default")) matches.add(BALANCED);
        if (matches.size() != 1) {
            throw new IllegalArgumentException("无法唯一识别控制级别，请明确说：警告、每次授权、全部禁止或平衡模式");
        }
        return matches.getFirst();
    }

    private static boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) if (value.contains(candidate)) return true;
        return false;
    }
}
