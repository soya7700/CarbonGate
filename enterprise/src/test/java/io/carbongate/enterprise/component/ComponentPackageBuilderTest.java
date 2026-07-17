package io.carbongate.enterprise.component;

import io.carbongate.json.Json;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class ComponentPackageBuilderTest {
    public static void run() throws Exception {
        Path home = Files.createTempDirectory("carbon-pack-builder-");
        Path source = home.resolve("source");
        Files.createDirectories(source.resolve("payload"));
        Files.writeString(source.resolve("manifest.json"), Json.stringify(Map.of(
                "apiVersion", ComponentManifest.API_VERSION, "kind", "pack",
                "metadata", Map.of("id", "custom-rules", "version", "1.0.0"),
                "spec", Map.of("entrypoint", List.of(), "operations", List.of(), "permissions", List.of(),
                        "timeoutMillis", 100, "failureMode", "fail_closed"),
                "license", Map.of("spdx", "Apache-2.0"))), StandardCharsets.UTF_8);
        Files.writeString(source.resolve("payload").resolve("pack.json"), Json.stringify(Map.of(
                "apiVersion", PackDocument.API_VERSION,
                "rules", List.of(Map.of("id", "custom.keyword", "audience", "both",
                        "category", "custom.keyword", "severity", "medium",
                        "match", Map.of("type", "keywords", "terms", List.of("internal-only")))))),
                StandardCharsets.UTF_8);
        Files.writeString(source.resolve("LICENSE"), "Apache-2.0 test fixture", StandardCharsets.UTF_8);
        Files.writeString(source.resolve("NOTICE"), "CarbonGate test Pack", StandardCharsets.UTF_8);

        Path archive = home.resolve("custom-rules.carbon");
        ComponentManifest manifest = new ComponentPackageBuilder().build(source, archive);
        assert manifest.kind() == ComponentManifest.Kind.PACK;
        ComponentManager manager = new ComponentManager(home.resolve("runtime"));
        manager.install(archive);
        Map<String, Object> enabled = manager.enable("custom-rules", "1.0.0");
        assert Json.stringify(enabled).contains("\"rules\":1");
    }
}
