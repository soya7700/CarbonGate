package io.carbongate.integration;

public enum Coverage {
    CONTROL_ONLY("CarbonGate status, rules, approvals and mode controls are available through MCP; host tool calls are not automatically intercepted"),
    MCP_ONLY("MCP traffic routed through CarbonGate is protected"),
    FULL("Host tool calls are routed through CarbonGate"),
    GUIDED("Manual host configuration is required"),
    UNSUPPORTED("No supported integration is available");

    private final String description;

    Coverage(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
