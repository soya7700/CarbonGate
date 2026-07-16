package io.carbongate.audit;

import io.carbongate.json.Json;
import io.carbongate.model.Action;
import io.carbongate.model.Evaluation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;

public final class AuditLog {
    private final Path path;

    public AuditLog(Path path) {
        this.path = path.toAbsolutePath().normalize();
    }

    public synchronized void append(Action action, Evaluation evaluation) throws IOException {
        Path parent = path.getParent();
        if (parent != null) Files.createDirectories(parent);
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("id", evaluation.id());
        event.put("at", evaluation.evaluatedAt().toString());
        event.put("actor", action.actor());
        event.put("capability", action.capability().name().toLowerCase());
        event.put("operation", action.operation());
        event.put("resource", evaluation.sanitizedResource());
        event.put("decision", evaluation.decision().name().toLowerCase());
        event.put("risk", evaluation.risk().name().toLowerCase());
        event.put("reason", evaluation.reason());
        event.put("findings", evaluation.findings());
        Files.writeString(path, Json.stringify(event) + System.lineSeparator(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
    }
}
