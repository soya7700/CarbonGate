package io.carbongate.enterprise.component;

import java.util.List;
import java.util.Map;

public final class PackDocumentTest {
    public static void run() {
        PackDocument document = PackDocument.from(Map.of("apiVersion", PackDocument.API_VERSION,
                "rules", List.of(rule("personal.phone", "personal", "personal.phone", "high", "phone_cn", List.of()),
                        rule("enterprise.assets", "enterprise", "enterprise.member-assets", "critical",
                                "keywords", List.of("会员余额", "储值余额")))));
        assert document.rules().size() == 2;
        assert document.rules().get(1).detector() == PackDocument.Detector.KEYWORDS;

        expectRejected(Map.of("apiVersion", PackDocument.API_VERSION,
                "rules", List.of(rule("unsafe.regex", "both", "custom.regex", "high", "regex", List.of(".*")))),
                "Unknown Pack detector");
        expectRejected(Map.of("apiVersion", PackDocument.API_VERSION,
                "rules", List.of(rule("duplicate.rule", "both", "custom.data", "medium", "keywords", List.of("a")),
                        rule("duplicate.rule", "both", "custom.data", "medium", "keywords", List.of("b")))),
                "duplicate rule id");
    }

    private static Map<String, Object> rule(String id, String audience, String category, String severity,
                                            String type, List<String> terms) {
        return Map.of("id", id, "audience", audience, "category", category, "severity", severity,
                "match", terms.isEmpty() ? Map.of("type", type) : Map.of("type", type, "terms", terms));
    }

    private static void expectRejected(Map<String, Object> value, String message) {
        try {
            PackDocument.from(value);
            throw new AssertionError("invalid Pack must be rejected");
        } catch (IllegalArgumentException expected) {
            assert expected.getMessage().contains(message) : expected.getMessage();
        }
    }
}
