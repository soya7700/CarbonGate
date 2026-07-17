package io.carbongate.integration;

import io.carbongate.cli.CarbonCli;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;

public final class IntegrationInvocation {
    private IntegrationInvocation() {}

    public static List<String> current() {
        return forArguments("mcp", "serve");
    }

    public static List<String> forArguments(String... arguments) {
        List<String> suffix = List.of(arguments);
        String configured = System.getenv("CARBON_BIN");
        if (configured != null && !configured.isBlank()) {
            return append(List.of(configured), suffix);
        }
        try {
            Path location = Path.of(CarbonCli.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (Files.isRegularFile(location) && location.getFileName().toString().endsWith(".jar")) {
                return append(List.of(javaExecutable(), "-jar", location.toAbsolutePath().normalize().toString()), suffix);
            }
        } catch (URISyntaxException | RuntimeException ignored) {
            // Development classpaths fall back to the installed launcher.
        }
        return append(List.of(windows() ? "carbon.cmd" : "carbon"), suffix);
    }

    private static List<String> append(List<String> prefix, List<String> suffix) {
        List<String> command = new ArrayList<>(prefix.size() + suffix.size());
        command.addAll(prefix);
        command.addAll(suffix);
        return List.copyOf(command);
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
