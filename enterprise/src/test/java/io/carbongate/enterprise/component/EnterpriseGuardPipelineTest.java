package io.carbongate.enterprise.component;

import io.carbongate.json.Json;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class EnterpriseGuardPipelineTest {
    public static void run() throws Exception {
        Path home = Files.createTempDirectory("carbon-pipeline-");
        ComponentManager manager = new ComponentManager(home);
        Path provider = component(home, "pipeline-provider", "provider",
                List.of("inspect", "authorize", "audit"), "fail_closed");
        Path sandbox = component(home, "pipeline-sandbox", "sandbox", List.of("sandbox"), "fail_closed");
        manager.install(provider);
        manager.install(sandbox);
        manager.enable("pipeline-provider", "1.0.0");
        manager.enable("pipeline-sandbox", "1.0.0");

        Map<String, Object> ask = manager.guard(Map.of("action", "shell", "risk", "high", "content", "safe",
                "sandbox", Map.of("componentId", "pipeline-sandbox", "payload", Map.of("command", List.of("check")))));
        assert ask.get("decision").equals("ask");
        assert !ask.containsKey("sandbox") : "ask decisions must not execute a sandbox";

        Map<String, Object> allowed = manager.guard(Map.of("action", "shell", "risk", "low", "content", "safe",
                "sandbox", Map.of("componentId", "pipeline-sandbox", "payload", Map.of("command", List.of("check")))));
        assert allowed.get("decision").equals("allow");
        assert Json.stringify(allowed).contains("test-sandbox");

        Map<String, Object> denied = manager.guard(Map.of("action", "egress", "risk", "low", "content", "blocked"));
        assert denied.get("decision").equals("deny");
        Map<String, Object> failedClosed = manager.guard(Map.of("action", "shell", "risk", "low", "content", "fail"));
        assert failedClosed.get("decision").equals("deny");
        assert Json.stringify(failedClosed).contains("failed_closed");

        Path openHome = Files.createTempDirectory("carbon-pipeline-open-");
        ComponentManager openManager = new ComponentManager(openHome);
        openManager.install(component(openHome, "pipeline-open", "provider", List.of("inspect"), "fail_open"));
        openManager.enable("pipeline-open", "1.0.0");
        Map<String, Object> failedOpen = openManager.guard(Map.of("action", "shell", "risk", "low", "content", "fail"));
        assert failedOpen.get("decision").equals("allow");
        assert Json.stringify(failedOpen).contains("failed_open");
    }

    private static Path component(Path directory, String id, String kind, List<String> operations,
                                  String failureMode) throws Exception {
        Path source = directory.resolve(id + "-source");
        Files.createDirectories(source.resolve("payload"));
        String java = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name", "").toLowerCase().contains("win") ? "java.exe" : "java").toString();
        Map<String, Object> manifest = Map.of("apiVersion", ComponentManifest.API_VERSION, "kind", kind,
                "metadata", Map.of("id", id, "version", "1.0.0"),
                "spec", Map.of("entrypoint", List.of(java, "-cp", System.getProperty("java.class.path"),
                                PipelineTestProviderMain.class.getName()), "operations", operations,
                        "permissions", List.of(), "timeoutMillis", 2000, "failureMode", failureMode),
                "license", Map.of("spdx", "Apache-2.0"));
        Files.writeString(source.resolve("manifest.json"), Json.stringify(manifest), StandardCharsets.UTF_8);
        Files.writeString(source.resolve("LICENSE"), "Apache-2.0 test fixture", StandardCharsets.UTF_8);
        Files.writeString(source.resolve("NOTICE"), "Pipeline test component", StandardCharsets.UTF_8);
        Files.writeString(source.resolve("payload").resolve("marker.txt"), "test", StandardCharsets.UTF_8);
        Path archive = directory.resolve(id + ".carbon");
        new ComponentPackageBuilder().build(source, archive);
        return archive;
    }
}
