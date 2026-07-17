package io.carbongate.enterprise.component;

import io.carbongate.json.Json;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public final class EchoProviderMain {
    private EchoProviderMain() {}

    public static void main(String[] args) throws Exception {
        String line = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)).readLine();
        Map<String, Object> request = Json.object(line);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("apiVersion", ProviderClient.API_VERSION);
        response.put("id", request.get("id"));
        response.put("status", "ok");
        response.put("result", request.get("operation").equals("health")
                ? Map.of("health", "pass") : request.get("payload"));
        System.out.println(Json.stringify(response));
    }
}
