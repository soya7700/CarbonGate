package io.carbongate.mcp;

import io.carbongate.audit.AuditLog;
import io.carbongate.json.Json;
import io.carbongate.model.Action;
import io.carbongate.model.Decision;
import io.carbongate.model.Evaluation;
import io.carbongate.policy.PolicyEngine;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/** Line-delimited JSON-RPC proxy for the MCP stdio transport. */
public final class McpProxy {
    private final Path workspace;
    private final PolicyEngine policy;
    private final AuditLog audit;
    private final McpToolClassifier classifier = new McpToolClassifier();

    public McpProxy(Path workspace, PolicyEngine policy, AuditLog audit) {
        this.workspace = workspace.toAbsolutePath().normalize();
        this.policy = policy;
        this.audit = audit;
    }

    public int run(List<String> serverCommand) throws IOException, InterruptedException {
        if (serverCommand.isEmpty()) throw new IllegalArgumentException("MCP server command is required");
        Process process = new ProcessBuilder(serverCommand)
                .directory(workspace.toFile())
                .start();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Thread requests = Thread.ofVirtual().start(() -> {
            try (var clientIn = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
                 var serverIn = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = clientIn.readLine()) != null) {
                    String forwarded = inspect(line);
                    if (forwarded != null) {
                        serverIn.write(forwarded);
                        serverIn.newLine();
                        serverIn.flush();
                    }
                }
            } catch (Throwable error) {
                failure.compareAndSet(null, error);
                process.destroy();
            }
        });

        Thread responses = Thread.ofVirtual().start(() -> {
            try (var serverOut = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = serverOut.readLine()) != null) {
                    System.out.println(line);
                    System.out.flush();
                }
            } catch (Throwable error) {
                failure.compareAndSet(null, error);
                process.destroy();
            }
        });

        Thread stderr = Thread.ofVirtual().start(() -> {
            try {
                process.getErrorStream().transferTo(System.err);
            } catch (IOException error) {
                failure.compareAndSet(null, error);
            }
        });

        int exit = process.waitFor();
        requests.interrupt();
        responses.join();
        stderr.join();
        if (failure.get() != null && !(failure.get() instanceof IOException)) {
            throw new IOException("MCP proxy failed", failure.get());
        }
        return exit;
    }

    private String inspect(String line) throws IOException {
        Map<String, Object> request;
        try {
            request = Json.object(line);
        } catch (IllegalArgumentException invalidJson) {
            return line;
        }
        Action action = classifier.classify(request, workspace);
        if (action == null) return line;
        Evaluation evaluation = policy.evaluate(action);
        audit.append(action, evaluation);
        if (evaluation.decision() == Decision.ALLOW) return line;

        Map<String, Object> errorData = new LinkedHashMap<>();
        errorData.put("decision", evaluation.decision().name().toLowerCase());
        errorData.put("risk", evaluation.risk().name().toLowerCase());
        errorData.put("findings", evaluation.findings());
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", -32001);
        error.put("message", "CarbonGate blocked tool call: " + evaluation.reason());
        error.put("data", errorData);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", request.get("id"));
        response.put("error", error);
        System.out.println(Json.stringify(response));
        System.out.flush();
        return null;
    }
}
