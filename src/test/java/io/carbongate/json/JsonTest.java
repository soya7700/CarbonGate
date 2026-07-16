package io.carbongate.json;

import java.util.List;
import java.util.Map;

public final class JsonTest {
    public static void run() {
        Map<String, Object> input = Map.of("text", "hello\nworld", "ok", true, "items", List.of(1, 2));
        Map<String, Object> parsed = Json.object(Json.stringify(input));
        assert parsed.get("text").equals("hello\nworld");
        assert parsed.get("ok").equals(Boolean.TRUE);
        assert ((List<?>) parsed.get("items")).size() == 2;
        try {
            Json.parse("{broken");
            throw new AssertionError("Invalid JSON should fail");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }
}
