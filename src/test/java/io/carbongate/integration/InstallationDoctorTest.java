package io.carbongate.integration;

import io.carbongate.config.SettingsStore;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class InstallationDoctorTest {
    public static void run() throws Exception {
        Path home = Files.createTempDirectory("carbon-doctor-");
        new SettingsStore(home).initialize();
        Path jar = Files.createTempFile("carbongate-doctor-", ".jar");
        CommandRunner runner = new MissingHostRunner();
        IntegrationRegistry registry = new IntegrationRegistry(home);
        IntegrationManager integrations = new IntegrationManager(registry, runner, HostCatalog.all(),
                List.of("java", "-jar", jar.toString(), "mcp", "serve"));
        InstallationDoctor doctor = new InstallationDoctor(home, integrations,
                List.of("java", "-jar", jar.toString(), "mcp", "serve"));

        Map<String, Object> healthy = doctor.diagnose();
        assert healthy.get("healthy").equals(true);
        String output = io.carbongate.json.Json.stringify(healthy);
        assert output.contains("local_log_limit");
        assert output.contains("10000000");
        assert output.contains("control_invocation");

        Files.createDirectories(registry.path().getParent());
        Files.writeString(registry.path(), "not-json", StandardCharsets.UTF_8);
        Map<String, Object> broken = doctor.diagnose();
        assert broken.get("healthy").equals(false);
        assert io.carbongate.json.Json.stringify(broken).contains("managed_registry_invalid");
    }

    private static final class MissingHostRunner implements CommandRunner {
        @Override
        public boolean available(String executable) {
            return false;
        }

        @Override
        public Result run(List<String> command) {
            throw new AssertionError("unavailable hosts must not be executed");
        }
    }
}
