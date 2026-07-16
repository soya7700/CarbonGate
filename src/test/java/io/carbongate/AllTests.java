package io.carbongate;

import io.carbongate.gateway.GatewayTest;
import io.carbongate.json.JsonTest;
import io.carbongate.mcp.McpToolClassifierTest;
import io.carbongate.policy.PolicyEngineTest;
import io.carbongate.security.FileBoundaryTest;
import io.carbongate.security.SecretScannerTest;
import io.carbongate.security.ShellRiskAnalyzerTest;

public final class AllTests {
    public static void main(String[] args) throws Exception {
        ShellRiskAnalyzerTest.run();
        FileBoundaryTest.run();
        SecretScannerTest.run();
        PolicyEngineTest.run();
        JsonTest.run();
        McpToolClassifierTest.run();
        GatewayTest.run();
        System.out.println("All CarbonGate tests passed.");
    }
}
