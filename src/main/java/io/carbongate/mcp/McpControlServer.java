package io.carbongate.mcp;

import io.carbongate.json.Json;
import io.carbongate.policy.PolicyProfile;
import io.carbongate.runtime.ControlService;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class McpControlServer {
    private static final String PROTOCOL_VERSION = "2024-11-05";
    private final ControlService controls;

    public McpControlServer(Path home, PolicyProfile profile) {
        controls = new ControlService(home, profile);
    }

    public int run() throws IOException {
        BufferedReader input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        PrintWriter output = new PrintWriter(System.out, true, StandardCharsets.UTF_8);
        String line;
        while ((line = input.readLine()) != null) {
            if (line.isBlank()) continue;
            Map<String, Object> response;
            try {
                response = handle(Json.object(line));
            } catch (RuntimeException | IOException error) {
                controls.recordError("mcp-control", compact(error));
                response = error(null, -32603, "CarbonGate control request failed: " + compact(error));
            }
            if (response != null) output.println(Json.stringify(response));
        }
        return 0;
    }

    public Map<String, Object> handle(Map<String, Object> request) throws IOException {
        Object id = request.get("id");
        String method = String.valueOf(request.get("method"));
        if ("notifications/initialized".equals(method) || "notifications/cancelled".equals(method)) return null;
        try {
            return switch (method) {
                case "initialize" -> success(id, Map.of(
                        "protocolVersion", PROTOCOL_VERSION,
                        "capabilities", Map.of("tools", Map.of("listChanged", false)),
                        "serverInfo", Map.of("name", "carbongate-control", "version", "0.2.0")));
                case "ping" -> success(id, Map.of());
                case "tools/list" -> success(id, Map.of("tools", tools()));
                case "tools/call" -> success(id, call(object(request.get("params"))));
                default -> error(id, -32601, "Method not found: " + method);
            };
        } catch (IllegalArgumentException error) {
            return error(id, -32602, compact(error));
        } catch (RuntimeException | IOException error) {
            controls.recordError("mcp-control", compact(error));
            return error(id, -32603, "CarbonGate control request failed: " + compact(error));
        }
    }

    private Map<String, Object> call(Map<String, Object> params) throws IOException {
        String name = string(params, "name");
        Map<String, Object> arguments = object(params.get("arguments"));
        Object value = switch (name) {
            case "carbon_status" -> controls.status();
            case "carbon_rules" -> controls.rules();
            case "carbon_blocked" -> controls.blocked(integer(arguments.get("limit"), 20));
            case "carbon_approvals" -> controls.approvals();
            case "carbon_approve" -> controls.approve(string(arguments, "id"));
            case "carbon_deny_approval" -> controls.deny(string(arguments, "id"));
            case "carbon_set_mode" -> controls.setMode(string(arguments, "instruction"));
            default -> throw new IllegalArgumentException("Unknown CarbonGate tool: " + name);
        };
        return Map.of("content", List.of(Map.of("type", "text", "text", Json.stringify(value))),
                "isError", false);
    }

    private List<Map<String, Object>> tools() {
        return List.of(
                tool("carbon_status", "Query CarbonGate mode, audit locations, pending approvals and log usage", schema()),
                tool("carbon_rules", "Show active CarbonGate security and logging rules", schema()),
                tool("carbon_blocked", "Show recent fully blocked actions and where they were recorded",
                        schema(Map.of("limit", Map.of("type", "integer", "minimum", 1, "maximum", 100)))),
                tool("carbon_approvals", "List actions waiting for one-time manual approval", schema()),
                tool("carbon_approve", "Approve one pending action exactly once",
                        requiredSchema("id", "string")),
                tool("carbon_deny_approval", "Deny and remove one pending approval",
                        requiredSchema("id", "string")),
                tool("carbon_set_mode", "Switch CarbonGate level using natural language, for example 警告提醒、每次授权、全部禁止 or 平衡模式",
                        requiredSchema("instruction", "string")));
    }

    private Map<String, Object> tool(String name, String description, Map<String, Object> inputSchema) {
        return Map.of("name", name, "description", description, "inputSchema", inputSchema);
    }

    private Map<String, Object> schema() {
        return schema(Map.of());
    }

    private Map<String, Object> schema(Map<String, Object> properties) {
        return Map.of("type", "object", "properties", properties, "additionalProperties", false);
    }

    private Map<String, Object> requiredSchema(String name, String type) {
        Map<String, Object> value = new LinkedHashMap<>(schema(Map.of(name, Map.of("type", type))));
        value.put("required", List.of(name));
        return value;
    }

    private Map<String, Object> success(Object id, Object result) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.put("result", result);
        return response;
    }

    private static Map<String, Object> error(Object id, int code, String message) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.put("error", Map.of("code", code, "message", message));
        return response;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> object(Object value) {
        if (value == null) return Map.of();
        if (value instanceof Map<?, ?> map) return (Map<String, Object>) map;
        throw new IllegalArgumentException("Expected an object");
    }

    private String string(Map<String, Object> value, String key) {
        Object raw = value.get(key);
        if (!(raw instanceof String text) || text.isBlank()) throw new IllegalArgumentException(key + " is required");
        return text;
    }

    private int integer(Object value, int fallback) {
        if (value == null) return fallback;
        if (value instanceof Number number) return number.intValue();
        throw new IllegalArgumentException("limit must be an integer");
    }

    private static String compact(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) message = error.getClass().getSimpleName();
        message = message.replace('\n', ' ').replace('\r', ' ').trim();
        return message.length() <= 256 ? message : message.substring(0, 256) + "…";
    }
}
