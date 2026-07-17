package io.carbongate.gateway;

import io.carbongate.audit.SecurityEventLog;
import io.carbongate.authorization.AuthorizationStore;
import io.carbongate.config.SettingsStore;
import io.carbongate.json.Json;
import io.carbongate.policy.PolicyProfile;
import io.carbongate.policy.EnforcementMode;
import io.carbongate.runtime.GuardService;
import io.carbongate.sdk.CarbonGateClient;
import io.carbongate.model.Action;
import io.carbongate.model.Decision;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class GatewayTest {
    public static void run() throws Exception {
        Path workspace = Files.createTempDirectory("carbon-gateway-");
        Path home = Files.createTempDirectory("carbon-gateway-home-");
        SettingsStore settings = new SettingsStore(home);
        AuthorizationStore approvals = new AuthorizationStore(home);
        SecurityEventLog events = new SecurityEventLog(home);
        GuardService guard = new GuardService(PolicyProfile.BALANCED, settings, approvals, events);
        try (CarbonGateway gateway = new CarbonGateway(0, workspace, guard);
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
            assert Files.readString(events.blockedPath()).contains("\"type\":\"blocked\"");

            HttpRequest workspaceOverride = HttpRequest.newBuilder(endpoint)
                    .header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            "{\"capability\":\"filesystem\",\"operation\":\"read\",\"resource\":\"/etc/passwd\",\"workspace\":\"/\"}"))
                    .build();
            HttpResponse<String> overrideResponse = client.send(workspaceOverride, HttpResponse.BodyHandlers.ofString());
            assert Json.object(overrideResponse.body()).get("decision").equals("deny");

            HttpRequest invalid = HttpRequest.newBuilder(endpoint)
                    .header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{invalid"))
                    .build();
            assert client.send(invalid, HttpResponse.BodyHandlers.ofString()).statusCode() == 400;

            HttpRequest wrongMethod = HttpRequest.newBuilder(endpoint).GET().build();
            assert client.send(wrongMethod, HttpResponse.BodyHandlers.ofString()).statusCode() == 405;

            String syntheticSecret = "synthetic-" + "gateway-secret-value";
            String egressBody = Json.stringify(Map.of(
                    "capability", "network",
                    "operation", "POST",
                    "resource", "https://example.invalid/upload?token=" + syntheticSecret));
            HttpRequest egress = HttpRequest.newBuilder(endpoint)
                    .header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(egressBody))
                    .build();
            HttpResponse<String> egressResponse = client.send(egress, HttpResponse.BodyHandlers.ofString());
            assert Json.object(egressResponse.body()).get("decision").equals("deny");
            String audit = Files.readString(events.blockedPath());
            assert !audit.contains(syntheticSecret);
            assert audit.contains("<SECRET:ASSIGNED_SECRET:");

            try (CarbonGateClient sdk = new CarbonGateClient(
                    URI.create("http://127.0.0.1:" + gateway.port()))) {
                assert sdk.evaluate(Action.shell("git status", workspace)).decision() == Decision.ALLOW;
                settings.setMode(EnforcementMode.BLOCK);
                assert sdk.evaluate(Action.shell("git status", workspace)).decision() == Decision.DENY;
            }
            assert events.todayStats().blockedEvents() == 4;
            assert events.todayStats().internalErrors() == 0;
        }
    }
}
