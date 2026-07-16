package io.carbongate.gateway;

import io.carbongate.audit.AuditLog;
import io.carbongate.json.Json;
import io.carbongate.policy.PolicyEngine;
import io.carbongate.policy.PolicyProfile;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

public final class GatewayTest {
    public static void run() throws Exception {
        Path workspace = Files.createTempDirectory("carbon-gateway-");
        try (CarbonGateway gateway = new CarbonGateway(0, workspace,
                new PolicyEngine(PolicyProfile.BALANCED),
                new AuditLog(workspace.resolve("audit.jsonl")));
             HttpClient client = HttpClient.newHttpClient()) {
            gateway.start();
            URI endpoint = URI.create("http://127.0.0.1:" + gateway.port() + "/v1/evaluate");
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            "{\"capability\":\"shell\",\"operation\":\"execute\",\"resource\":\"rm -rf /\"}"))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assert response.statusCode() == 200;
            assert Json.object(response.body()).get("decision").equals("deny");
            assert Files.readString(workspace.resolve("audit.jsonl")).contains("\"decision\":\"deny\"");

            HttpRequest workspaceOverride = HttpRequest.newBuilder(endpoint)
                    .header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            "{\"capability\":\"filesystem\",\"operation\":\"read\",\"resource\":\"/etc/passwd\",\"workspace\":\"/\"}"))
                    .build();
            HttpResponse<String> overrideResponse = client.send(workspaceOverride, HttpResponse.BodyHandlers.ofString());
            assert Json.object(overrideResponse.body()).get("decision").equals("deny");
        }
    }
}
