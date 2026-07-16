package io.carbongate.mcp;

import io.carbongate.json.Json;
import io.carbongate.model.Action;
import io.carbongate.model.Capability;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class McpToolClassifier {
    Action classify(Map<String, Object> request, Path workspace) {
        if (!"tools/call".equals(request.get("method"))) return null;
        Map<String, Object> params = object(request.get("params"));
        String name = String.valueOf(params.getOrDefault("name", "unknown"));
        Map<String, Object> args = object(params.get("arguments"));
        String lower = name.toLowerCase(Locale.ROOT);

        if (containsAny(lower, "shell", "command", "terminal", "exec", "bash")) {
            String command = first(args, "command", "cmd", "script", "input");
            if (command == null) command = Json.stringify(args);
            return new Action("mcp:" + name, Capability.SHELL, "execute", command, workspace, Map.of("tool", name));
        }
        if (containsAny(lower, "file", "directory", "read", "write", "edit", "delete", "move")) {
            String path = fileResource(args, workspace);
            if (path == null) path = Json.stringify(args);
            String operation = containsAny(lower, "delete", "remove") ? "delete"
                    : containsAny(lower, "write", "edit", "create", "move") ? "write" : "read";
            return new Action("mcp:" + name, Capability.FILESYSTEM, operation, path, workspace, Map.of("tool", name));
        }
        if (containsAny(lower, "http", "fetch", "request", "url", "download", "upload")) {
            String url = first(args, "url", "uri", "endpoint");
            if (url == null) url = Json.stringify(args);
            String method = first(args, "method");
            if (method == null) method = containsAny(lower, "upload", "post") ? "POST" : "GET";
            return new Action("mcp:" + name, Capability.NETWORK, method, url, workspace, Map.of("tool", name));
        }
        return new Action("mcp:" + name, Capability.UNKNOWN, "call", Json.stringify(args), workspace, Map.of("tool", name));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> object(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private String first(Map<String, Object> args, String... keys) {
        for (String key : keys) {
            Object value = args.get(key);
            if (value != null) return String.valueOf(value);
        }
        return null;
    }

    private String fileResource(Map<String, Object> args, Path workspace) {
        List<String> paths = new ArrayList<>();
        for (String key : List.of("path", "file", "filePath", "source", "target", "destination")) {
            Object value = args.get(key);
            if (value != null) paths.add(String.valueOf(value));
        }
        if (paths.isEmpty()) return null;
        var boundary = new io.carbongate.security.FileBoundary(workspace);
        for (String path : paths) {
            try {
                boundary.resolve(path);
            } catch (Exception denied) {
                return path;
            }
        }
        return paths.getFirst();
    }

    private boolean containsAny(String value, String... needles) {
        for (String needle : needles) if (value.contains(needle)) return true;
        return false;
    }
}
