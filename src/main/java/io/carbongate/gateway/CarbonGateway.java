package io.carbongate.gateway;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.carbongate.audit.AuditLog;
import io.carbongate.json.Json;
import io.carbongate.model.Action;
import io.carbongate.model.Evaluation;
import io.carbongate.policy.PolicyEngine;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Executors;

public final class CarbonGateway implements AutoCloseable {
    private static final int MAX_BODY_BYTES = 1_048_576;
    private final HttpServer server;
    private final PolicyEngine policy;
    private final Path workspace;
    private final AuditLog auditLog;

    public CarbonGateway(int port, Path workspace, PolicyEngine policy, AuditLog auditLog) throws IOException {
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 64);
        this.workspace = workspace.toAbsolutePath().normalize();
        this.policy = policy;
        this.auditLog = auditLog;
        this.server.createContext("/v1/health", this::health);
        this.server.createContext("/v1/evaluate", this::evaluate);
        this.server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
    }

    public void start() {
        server.start();
    }

    public int port() {
        return server.getAddress().getPort();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void health(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) {
            respond(exchange, 405, Map.of("error", "method_not_allowed"));
            return;
        }
        respond(exchange, 200, Map.of("status", "ok", "version", "0.1.0"));
    }

    private void evaluate(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("POST")) {
            respond(exchange, 405, Map.of("error", "method_not_allowed"));
            return;
        }
        try {
            byte[] body = exchange.getRequestBody().readNBytes(MAX_BODY_BYTES + 1);
            if (body.length > MAX_BODY_BYTES) {
                respond(exchange, 413, Map.of("error", "request_too_large"));
                return;
            }
            Action action = EvaluationMapper.actionFrom(
                    Json.object(new String(body, StandardCharsets.UTF_8)), workspace);
            Evaluation evaluation = policy.evaluate(action);
            auditLog.append(action, evaluation);
            respond(exchange, 200, EvaluationMapper.toMap(evaluation));
        } catch (IllegalArgumentException e) {
            respond(exchange, 400, Map.of("error", "invalid_request", "message", e.getMessage()));
        }
    }

    private void respond(HttpExchange exchange, int status, Map<String, Object> body) throws IOException {
        byte[] bytes = Json.stringify(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("content-type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("cache-control", "no-store");
        exchange.getResponseHeaders().set("x-content-type-options", "nosniff");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
