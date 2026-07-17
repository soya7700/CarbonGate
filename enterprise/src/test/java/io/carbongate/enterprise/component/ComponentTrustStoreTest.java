package io.carbongate.enterprise.component;

import io.carbongate.json.Json;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class ComponentTrustStoreTest {
    public static void run() throws Exception {
        Path root = Files.createTempDirectory("carbon-trust-");
        Path source = source(root);
        Path home = root.resolve("home");
        ComponentTrustStore trust = new ComponentTrustStore(home);
        Map<String, Object> generated = trust.generate("test-publisher", root.resolve("keys"));
        trust.add("test-publisher", Path.of(String.valueOf(generated.get("publicKey"))));
        trust.policy("require_signed");
        Path signed = root.resolve("signed.carbon");
        new ComponentPackageBuilder().build(source, signed, "test-publisher",
                Path.of(String.valueOf(generated.get("privateKey"))));
        new ComponentManager(home).install(signed);

        Path unsigned = root.resolve("unsigned.carbon");
        new ComponentPackageBuilder().build(source, unsigned);
        expectRejected(() -> new ComponentManager(root.resolve("unsigned-home-required")).install(unsigned), false);
        ComponentTrustStore required = new ComponentTrustStore(root.resolve("unsigned-home-required"));
        required.policy("require_signed");
        expectRejected(() -> new ComponentManager(root.resolve("unsigned-home-required")).install(unsigned), true);

        Path wrongHome = root.resolve("wrong-home");
        ComponentTrustStore wrong = new ComponentTrustStore(wrongHome);
        Map<String, Object> wrongKey = wrong.generate("test-publisher", root.resolve("wrong-keys"));
        wrong.add("test-publisher", Path.of(String.valueOf(wrongKey.get("publicKey"))));
        wrong.policy("require_signed");
        expectRejected(() -> new ComponentManager(wrongHome).install(signed), true);
        assert trust.status().get("policy").equals("require_signed");
    }

    private static Path source(Path root) throws Exception {
        Path source = root.resolve("source");
        Files.createDirectories(source.resolve("payload"));
        Files.writeString(source.resolve("manifest.json"), Json.stringify(Map.of(
                "apiVersion", ComponentManifest.API_VERSION, "kind", "pack",
                "metadata", Map.of("id", "signed-pack", "version", "1.0.0"),
                "spec", Map.of("entrypoint", List.of(), "operations", List.of(), "permissions", List.of(),
                        "timeoutMillis", 100, "failureMode", "fail_closed"),
                "license", Map.of("spdx", "Apache-2.0"))), StandardCharsets.UTF_8);
        Files.writeString(source.resolve("payload/pack.json"), Json.stringify(Map.of(
                "apiVersion", PackDocument.API_VERSION, "rules", List.of())), StandardCharsets.UTF_8);
        Files.writeString(source.resolve("LICENSE"), "Apache-2.0 fixture", StandardCharsets.UTF_8);
        Files.writeString(source.resolve("NOTICE"), "Signed Pack fixture", StandardCharsets.UTF_8);
        return source;
    }

    private static void expectRejected(ThrowingAction action, boolean shouldReject) throws Exception {
        try {
            action.run();
            if (shouldReject) throw new AssertionError("untrusted package must be rejected");
        } catch (IllegalArgumentException expected) {
            if (!shouldReject) throw expected;
            assert expected.getMessage().contains("signed") || expected.getMessage().contains("verification");
        }
    }

    @FunctionalInterface
    private interface ThrowingAction {
        void run() throws Exception;
    }
}
