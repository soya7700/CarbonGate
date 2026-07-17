package io.carbongate.config;

import java.nio.file.Path;

public final class CarbonHome {
    private CarbonHome() {}

    public static Path resolve() {
        String configured = System.getenv("CARBON_HOME");
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured).toAbsolutePath().normalize();
        }
        return Path.of(System.getProperty("user.home"), ".carbongate")
                .toAbsolutePath().normalize();
    }
}
