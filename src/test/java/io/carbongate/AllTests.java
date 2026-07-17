package io.carbongate;

import io.carbongate.audit.SecurityEventLogTest;
import io.carbongate.audit.EnterpriseAuditLogTest;
import io.carbongate.authorization.AuthorizationStoreTest;
import io.carbongate.config.SettingsStoreTest;
import io.carbongate.gateway.GatewayTest;
import io.carbongate.json.JsonTest;
import io.carbongate.mcp.McpToolClassifierTest;
import io.carbongate.policy.PolicyEngineTest;
import io.carbongate.runtime.GuardServiceTest;
import io.carbongate.runtime.WarningLimiterTest;
import io.carbongate.security.FileBoundaryTest;
import io.carbongate.security.SecretScannerTest;
import io.carbongate.security.ShellRiskAnalyzerTest;

public final class AllTests {
    public static void main(String[] args) throws Exception {
        ShellRiskAnalyzerTest.run();
        SettingsStoreTest.run();
        AuthorizationStoreTest.run();
        SecurityEventLogTest.run();
        EnterpriseAuditLogTest.run();
        FileBoundaryTest.run();
        SecretScannerTest.run();
        PolicyEngineTest.run();
        GuardServiceTest.run();
        WarningLimiterTest.run();
        JsonTest.run();
        McpToolClassifierTest.run();
        GatewayTest.run();
        System.out.println("All CarbonGate tests passed.");
    }
}
