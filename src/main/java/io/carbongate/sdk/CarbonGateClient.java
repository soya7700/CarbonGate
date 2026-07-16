package io.carbongate.sdk;

import io.carbongate.json.Json;
import io.carbongate.model.Action;
import io.carbongate.model.Decision;
import io.carbongate.model.Evaluation;
import io.carbongate.model.RiskLevel;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Framework-neutral Java 21 client for a local or sidecar CarbonGate. */
public final class CarbonGateClient implements AutoCloseable {
    private final URI endpoint;
    private final HttpClient client;

    public CarbonGateClient(URI endpoint) {
        this.endpoint = endpoint;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    public Evaluation evaluate(Action action) throws IOException, InterruptedException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("actor", action.actor());
        body.put("capability", action.capability().name().toLowerCase());
        body.put("operation", action.operation());
        body.put("resource", action.resource());
        body.put("workspace", action.workspace().toString());

        HttpRequest request = HttpRequest.newBuilder(endpoint.resolve("/v1/evaluate"))
                .timeout(Duration.ofSeconds(10))
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(Json.stringify(body)))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("CarbonGate returned HTTP " + response.statusCode() + ": " + response.body());
        }
        return evaluation(Json.object(response.body()));
    }

    @SuppressWarnings("unchecked")
    private Evaluation evaluation(Map<String, Object> value) {
        Object rawFindings = value.get("findings");
        List<String> findings = rawFindings instanceof List<?> list
                ? list.stream().map(String::valueOf).toList()
                : List.of();
        return new Evaluation(
                String.valueOf(value.get("id")),
                Decision.valueOf(String.valueOf(value.get("decision")).toUpperCase()),
                RiskLevel.valueOf(String.valueOf(value.get("risk")).toUpperCase()),
                String.valueOf(value.get("reason")),
                findings,
                String.valueOf(value.get("sanitizedResource")),
                Instant.parse(String.valueOf(value.get("evaluatedAt")))
        );
    }

    @Override
    public void close() {
        client.close();
    }
}
