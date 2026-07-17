package io.carbongate.provider.dlp;

import io.carbongate.json.Json;

import java.util.List;
import java.util.Map;

public final class SensitiveDataProviderTest {
    public static void main(String[] args) {
        Map<String, Object> rule = Map.of("id", "personal.id", "audience", "personal",
                "category", "personal.identity", "severity", "critical", "match", Map.of("type", "id_cn"));
        Map<String, Object> keyword = Map.of("id", "enterprise.assets", "audience", "enterprise",
                "category", "enterprise.member-assets", "severity", "high",
                "match", Map.of("type", "keywords", "terms", List.of("会员余额")));
        Map<String, Object> pack = Map.of("id", "test-pack", "version", "1.0.0",
                "document", Map.of("rules", List.of(rule, keyword)));
        String sensitive = "身份证 11010519491231002X，会员余额 900";
        Map<String, Object> result = SensitiveDataProvider.inspect(Map.of("text", sensitive,
                "_carbongate", Map.of("activePacks", List.of(pack))));
        String json = Json.stringify(result);
        assert result.get("decision").equals("block");
        assert result.get("findingCount").equals(2);
        assert !json.contains("11010519491231002X") : "Provider response must not return sensitive content";
        assert result.get("contentReturned").equals(false);
        System.out.println("Sensitive Data Provider tests passed.");
    }
}
