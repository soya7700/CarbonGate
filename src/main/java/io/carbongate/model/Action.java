package io.carbongate.model;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

public record Action(
        String actor,
        Capability capability,
        String operation,
        String resource,
        Path workspace,
        Map<String, String> context
) {
    public Action {
        actor = Objects.requireNonNullElse(actor, "unknown-agent");
        capability = Objects.requireNonNullElse(capability, Capability.UNKNOWN);
        operation = Objects.requireNonNullElse(operation, "unknown");
        resource = Objects.requireNonNullElse(resource, "");
        workspace = Objects.requireNonNullElse(workspace, Path.of(".")).toAbsolutePath().normalize();
        context = context == null ? Map.of() : Map.copyOf(context);
    }

    public static Action shell(String command, Path workspace) {
        return new Action("local-cli", Capability.SHELL, "execute", command, workspace, Map.of());
    }

    public static Action file(String operation, String path, Path workspace) {
        return new Action("local-cli", Capability.FILESYSTEM, operation, path, workspace, Map.of());
    }
}
