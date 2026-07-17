package io.carbongate.integration;

import io.carbongate.cli.CarbonCli;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class IntegrationInvocation {
    private IntegrationInvocation() {}

    public static List<String> current() {
        String configured = System.getenv("CARBON_BIN");
        if (configured != null && !configured.isBlank()) {
            return List.of(configured, "mcp", "serve");
        }
        try {
            Path location = Path.of(CarbonCli.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (Files.isRegularFile(location) && location.getFileName().toString().endsWith(".jar")) {
                return List.of(javaExecutable(), "-jar", location.toAbsolutePath().normalize().toString(),
                        "mcp", "serve");
            }
        } catch (URISyntaxException | RuntimeException ignored) {
            // Development classpaths fall back to the installed launcher.
        }
        return List.of(windows() ? "carbon.cmd" : "carbon", "mcp", "serve");
    }

    private static String javaExecutable() {
        String name = windows() ? "java.exe" : "java";
        Path bundled = Path.of(System.getProperty("java.home"), "bin", name);
        return Files.isRegularFile(bundled) ? bundled.toString() : name;
    }

    private static boolean windows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
