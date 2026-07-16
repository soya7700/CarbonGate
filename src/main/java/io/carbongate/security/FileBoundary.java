package io.carbongate.security;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

public final class FileBoundary {
    private final Path workspace;

    public FileBoundary(Path workspace) {
        this.workspace = workspace.toAbsolutePath().normalize();
    }

    public Path resolve(String requestedPath) throws IOException {
        if (requestedPath == null || requestedPath.isBlank()) {
            throw new SecurityException("File path is empty");
        }
        Path requested = Path.of(requestedPath);
        Path candidate = (requested.isAbsolute() ? requested : workspace.resolve(requested))
                .toAbsolutePath().normalize();
        if (!candidate.startsWith(workspace)) {
            throw new SecurityException("Path escapes workspace: " + requestedPath);
        }

        Path workspaceReal = Files.exists(workspace)
                ? workspace.toRealPath()
                : workspace;
        Path existing = nearestExisting(candidate);
        if (existing != null) {
            Path existingReal = existing.toRealPath();
            if (!existingReal.startsWith(workspaceReal)) {
                throw new SecurityException("Path crosses a symlink outside workspace: " + requestedPath);
            }
        }
        return candidate;
    }

    private Path nearestExisting(Path candidate) {
        Path current = candidate;
        while (current != null && current.startsWith(workspace)) {
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) return current;
            current = current.getParent();
        }
        return null;
    }
}
