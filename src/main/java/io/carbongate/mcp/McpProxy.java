package io.carbongate.mcp;

import io.carbongate.json.Json;
import io.carbongate.model.Action;
import io.carbongate.model.Decision;
import io.carbongate.model.Evaluation;
import io.carbongate.model.RiskLevel;
import io.carbongate.runtime.GuardService;
import io.carbongate.runtime.WarningLimiter;

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
    private final GuardService guard;
    private final WarningLimiter warnings;
    private final McpToolClassifier classifier = new McpToolClassifier();

    public McpProxy(Path workspace, GuardService guard) {
        this.workspace = workspace.toAbsolutePath().normalize();
        this.guard = guard;
        this.warnings = new WarningLimiter(guard.consoleDailyWarningLimit());
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
                guard.audit().recordError("mcp-request", error.getMessage());
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
                guard.audit().recordError("mcp-response", error.getMessage());
                process.destroy();
            }
        });

        Thread stderr = Thread.ofVirtual().start(() -> {
            try {
                process.getErrorStream().transferTo(System.err);
            } catch (IOException error) {
                failure.compareAndSet(null, error);
                guard.audit().recordError("mcp-stderr", error.getMessage());
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
        Evaluation evaluation = guard.evaluate(action);
        if (evaluation.decision() == Decision.ALLOW) {
            if (evaluation.risk().atLeast(RiskLevel.MEDIUM)) {
                WarningLimiter.Result warning = warnings.acquire();
                if (warning == WarningLimiter.Result.EMIT) {
                    System.err.printf("CarbonGate WARNING [%s]: %s%n",
                            evaluation.risk().name().toLowerCase(), evaluation.reason());
                } else if (warning == WarningLimiter.Result.EMIT_SUPPRESSION_NOTICE) {
                    System.err.println("CarbonGate WARNING: daily console warning limit reached; further warnings are suppressed.");
                }
            }
            return line;
        }

        Map<String, Object> errorData = new LinkedHashMap<>();
        errorData.put("approvalId", evaluation.id());
        errorData.put("decision", evaluation.decision().name().toLowerCase());
        errorData.put("risk", evaluation.risk().name().toLowerCase());
        errorData.put("findings", evaluation.findings());
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", -32001);
        String message = evaluation.decision() == Decision.ASK
                ? "CarbonGate requires manual approval: " + evaluation.reason()
                : "CarbonGate blocked tool call: " + evaluation.reason();
        error.put("message", message);
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
