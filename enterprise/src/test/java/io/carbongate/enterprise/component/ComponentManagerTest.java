package io.carbongate.enterprise.component;

import io.carbongate.json.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

public final class ComponentManagerTest {
    public static void run() throws Exception {
        Path home = Files.createTempDirectory("carbon-enterprise-components-");
        ComponentManager manager = new ComponentManager(home);
        Path first = component(home, "test-provider", "1.0.0", EchoProviderMain.class, 2_000, false);
        Map<String, Object> installed = manager.install(first);
        assert installed.get("state").equals("installed_disabled");
        assert manager.list().size() == 1;
        assert manager.list().getFirst().get("enabled").equals(false);

        Map<String, Object> enabled = manager.enable("test-provider", "1.0.0");
        assert enabled.get("state").equals("enabled");
        Map<String, Object> response = manager.invoke("test-provider", "inspect", Map.of("value", "safe"));
        assert Json.stringify(response).contains("safe");
        assert manager.doctor().get("healthy").equals(true);

        Path second = component(home, "test-provider", "1.1.0", EchoProviderMain.class, 2_000, false);
        manager.install(second);
        manager.enable("test-provider", "1.1.0");
        assert manager.list().stream().filter(value -> Boolean.TRUE.equals(value.get("enabled")))
                .findFirst().orElseThrow().get("version").equals("1.1.0");
        manager.enable("test-provider", "1.0.0");
        assert manager.list().stream().filter(value -> Boolean.TRUE.equals(value.get("enabled")))
                .findFirst().orElseThrow().get("version").equals("1.0.0") : "enabling an older version is an atomic rollback";
        manager.disable("test-provider");
        manager.remove("test-provider", "1.1.0");

        Path slow = component(home, "slow-provider", "1.0.0", SlowProviderMain.class, 100, false);
        manager.install(slow);
        try {
            manager.enable("slow-provider", "1.0.0");
            throw new AssertionError("timed-out provider must not be enabled");
        } catch (IOException expected) {
            assert expected.getMessage().contains("timed out");
        }
        assert manager.list().stream().filter(value -> value.get("id").equals("slow-provider"))
                .noneMatch(value -> Boolean.TRUE.equals(value.get("enabled")));

        Path pack = packComponent(home);
        manager.install(pack);
        Map<String, Object> packEnabled = manager.enable("baseline-pack", "1.0.0");
        assert Json.stringify(packEnabled).contains("data_only");
        manager.disable("baseline-pack");
        manager.remove("baseline-pack", "1.0.0");

        Path badChecksum = component(home, "bad-checksum", "1.0.0", EchoProviderMain.class, 2_000, true);
        expectRejected(() -> manager.install(badChecksum), "checksum mismatch");
        Path traversal = traversalPackage(home);
        expectRejected(() -> manager.install(traversal), "escapes staging");
        Path duplicate = duplicatePackage(home);
        expectRejected(() -> manager.install(duplicate), "duplicate path");

        try {
            ComponentManifest.from(manifest("invalid-pack", "1.0.0", EchoProviderMain.class, 2_000, "pack"));
            throw new AssertionError("Pack must never declare executable code");
        } catch (IllegalArgumentException expected) {
            assert expected.getMessage().contains("cannot execute code");
        }
    }

    private static Path component(Path directory, String id, String version, Class<?> mainClass,
                                  int timeout, boolean badChecksum) throws Exception {
        byte[] marker = "provider-payload".getBytes(StandardCharsets.UTF_8);
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(marker));
        Map<String, Object> checksums = Map.of("algorithm", "SHA-256",
                "files", Map.of("payload/marker.txt", badChecksum ? "0".repeat(64) : hash));
        Path archive = directory.resolve(id + "-" + version + ".carbon");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(archive))) {
            entry(output, "manifest.json", Json.stringify(manifest(id, version, mainClass, timeout, "provider")));
            entry(output, "checksums.json", Json.stringify(checksums));
            entry(output, "LICENSE", "Apache License 2.0 test fixture");
            entry(output, "NOTICE", "CarbonGate test provider");
            entry(output, "payload/marker.txt", marker);
        }
        return archive;
    }

    private static Map<String, Object> manifest(String id, String version, Class<?> mainClass,
                                                int timeout, String kind) {
        String java = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name", "").toLowerCase().contains("win") ? "java.exe" : "java").toString();
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("apiVersion", ComponentManifest.API_VERSION);
        root.put("kind", kind);
        root.put("metadata", Map.of("id", id, "version", version));
        root.put("spec", Map.of("entrypoint", List.of(java, "-cp", System.getProperty("java.class.path"),
                        mainClass.getName()), "operations", List.of("inspect"),
                "permissions", List.of("data.sanitized"), "timeoutMillis", timeout,
                "failureMode", "fail_closed"));
        root.put("license", Map.of("spdx", "Apache-2.0"));
        return root;
    }

    private static Path traversalPackage(Path directory) throws IOException {
        Path archive = directory.resolve("traversal.carbon");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(archive))) {
            entry(output, "../escape", "denied");
        }
        return archive;
    }

    private static Path duplicatePackage(Path directory) throws IOException {
        Path archive = directory.resolve("duplicate.carbon");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(archive))) {
            entry(output, "payload/marker.txt", "first");
            entry(output, "payload//marker.txt", "second");
        }
        return archive;
    }

    private static Path packComponent(Path directory) throws Exception {
        byte[] policy = "{\"rules\":[]}".getBytes(StandardCharsets.UTF_8);
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(policy));
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("apiVersion", ComponentManifest.API_VERSION);
        manifest.put("kind", "pack");
        manifest.put("metadata", Map.of("id", "baseline-pack", "version", "1.0.0"));
        manifest.put("spec", Map.of("entrypoint", List.of(), "operations", List.of(),
                "permissions", List.of(), "timeoutMillis", 100, "failureMode", "fail_closed"));
        manifest.put("license", Map.of("spdx", "Apache-2.0"));
        Path archive = directory.resolve("baseline-pack.carbon");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(archive))) {
            entry(output, "manifest.json", Json.stringify(manifest));
            entry(output, "checksums.json", Json.stringify(Map.of("algorithm", "SHA-256",
                    "files", Map.of("payload/policy.json", hash))));
            entry(output, "LICENSE", "Apache License 2.0 test fixture");
            entry(output, "NOTICE", "CarbonGate test pack");
            entry(output, "payload/policy.json", policy);
        }
        return archive;
    }

    private static void entry(JarOutputStream output, String name, String value) throws IOException {
        entry(output, name, value.getBytes(StandardCharsets.UTF_8));
    }

    private static void entry(JarOutputStream output, String name, byte[] value) throws IOException {
        output.putNextEntry(new JarEntry(name));
        output.write(value);
        output.closeEntry();
    }

    private static void expectRejected(ThrowingAction action, String message) throws Exception {
        try {
            action.run();
            throw new AssertionError("unsafe component package must be rejected");
        } catch (IllegalArgumentException expected) {
            assert expected.getMessage().contains(message) : expected.getMessage();
        }
    }

    @FunctionalInterface
    private interface ThrowingAction {
        void run() throws Exception;
    }
}
