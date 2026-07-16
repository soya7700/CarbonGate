package io.carbongate.security;

import java.nio.file.Files;
import java.nio.file.Path;

public final class FileBoundaryTest {
    public static void run() throws Exception {
        Path workspace = Files.createTempDirectory("carbon-boundary-");
        FileBoundary boundary = new FileBoundary(workspace);
        assert boundary.resolve("src/Main.java").startsWith(workspace);
        expectDenied(() -> boundary.resolve("../secret.txt"));
        expectDenied(() -> boundary.resolve(workspace.resolve("../secret.txt").toString()));

        Path outside = Files.createTempDirectory("carbon-outside-");
        Path link = workspace.resolve("escape");
        try {
            Files.createSymbolicLink(link, outside);
            expectDenied(() -> boundary.resolve("escape/secret.txt"));
        } catch (UnsupportedOperationException ignored) {
            // Some Windows environments do not grant symlink creation privileges.
        }
    }

    private static void expectDenied(CheckedRunnable action) throws Exception {
        try {
            action.run();
            throw new AssertionError("Expected path to be denied");
        } catch (SecurityException expected) {
            // expected
        }
    }

    @FunctionalInterface
    private interface CheckedRunnable {
        void run() throws Exception;
    }
}
