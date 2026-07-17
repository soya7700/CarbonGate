package io.carbongate.config;

import io.carbongate.policy.EnforcementMode;

import java.nio.file.Files;
import java.nio.file.Path;

public final class SettingsStoreTest {
    public static void run() throws Exception {
        Path home = Files.createTempDirectory("carbon-settings-");
        SettingsStore settings = new SettingsStore(home);
        assert settings.mode() == EnforcementMode.BALANCED;

        settings.setMode(EnforcementMode.APPROVAL);
        assert settings.mode() == EnforcementMode.APPROVAL;
        assert Files.size(settings.path()) < 1_000;
        settings.set(SettingsStore.RULE_NETWORK, "false");
        assert !settings.rules().network();
        assert settings.rules().shell();
        settings.set(SettingsStore.AUDIT_MODE, "enterprise_detailed");
        assert settings.auditMode() == AuditMode.ENTERPRISE_DETAILED;
        try {
            settings.set(SettingsStore.LOCAL_LOG_LIMIT, "1000001");
            throw new AssertionError("Local-agent log cap must not exceed 1 MB");
        } catch (IllegalArgumentException expected) {
            // expected
        }

        assert EnforcementMode.fromNaturalLanguage("切换到警告提醒") == EnforcementMode.WARN;
        assert EnforcementMode.fromNaturalLanguage("以后每次都要手动授权") == EnforcementMode.APPROVAL;
        assert EnforcementMode.fromNaturalLanguage("完全拦截所有操作") == EnforcementMode.BLOCK;
        assert EnforcementMode.fromNaturalLanguage("恢复默认平衡模式") == EnforcementMode.BALANCED;
        try {
            EnforcementMode.fromNaturalLanguage("随便设置一下");
            throw new AssertionError("Ambiguous natural language must be rejected");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }
}
