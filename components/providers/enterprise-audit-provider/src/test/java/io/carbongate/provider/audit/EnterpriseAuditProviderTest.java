package io.carbongate.provider.audit;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class EnterpriseAuditProviderTest {
    public static void main(String[] args) throws Exception {
        Path component = Files.createTempDirectory("carbon-audit-provider-");
        Map<String, Object> event = Map.of("eventId", "event-1", "action", "shell", "risk", "high",
                "decision", "ask", "components", List.of("approval:authorize"),
                "content", "must-not-be-stored");
        Map<String, Object> first = EnterpriseAuditProvider.append(event, component);
        Map<String, Object> second = EnterpriseAuditProvider.append(Map.of("eventId", "event-2", "action", "shell",
                "risk", "low", "decision", "allow", "components", List.of()), component);
        assert first.get("sequence").equals(1L);
        assert second.get("sequence").equals(2L);
        assert EnterpriseAuditProvider.health(component).get("chain").equals("valid");
        Path log;
        try (var files = Files.list(component.resolve("state"))) {
            log = files.findFirst().orElseThrow();
        }
        String value = Files.readString(log, StandardCharsets.UTF_8);
        assert !value.contains("must-not-be-stored");
        Files.writeString(log, value.replaceFirst("\"decision\":\"ask\"", "\"decision\":\"deny\""),
                StandardCharsets.UTF_8);
        try {
            EnterpriseAuditProvider.health(component);
            throw new AssertionError("tampered audit tail must fail health");
        } catch (java.io.IOException expected) {
            assert expected.getMessage().contains("invalid");
        }
        System.out.println("Enterprise Audit Provider tests passed.");
    }
}
