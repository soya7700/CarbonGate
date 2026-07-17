package io.carbongate.audit;

import io.carbongate.model.Action;
import io.carbongate.policy.EnforcementMode;
import io.carbongate.policy.PolicyEngine;
import io.carbongate.policy.PolicyProfile;

import java.nio.file.Files;
import java.nio.file.Path;

public final class SecurityEventLogTest {
    public static void run() throws Exception {
        Path home = Files.createTempDirectory("carbon-events-");
        SecurityEventLog events = new SecurityEventLog(home, 900);
        Action blocked = Action.shell("rm -rf / password=synthetic-log-secret", home);
        var evaluation = new PolicyEngine(PolicyProfile.BALANCED, EnforcementMode.BLOCK).evaluate(blocked);

        for (int index = 0; index < 20; index++) events.recordBlocked(blocked, evaluation);
        var stats = events.todayStats();
        assert stats.bytesWritten() <= 900;
        assert stats.blockedEvents() > 0;
        assert stats.blockedEvents() < 20;
        String content = Files.readString(events.blockedPath());
        assert content.contains("\"level\":\"ERROR\"");
        assert !content.contains("synthetic-log-secret");

        Path secondHome = Files.createTempDirectory("carbon-errors-");
        SecurityEventLog errors = new SecurityEventLog(secondHome, 10_000);
        assert errors.recordError("test", "password=synthetic-error-secret");
        String errorContent = Files.readString(errors.errorPath());
        assert errorContent.contains("\"type\":\"internal_error\"");
        assert !errorContent.contains("synthetic-error-secret");

        Path concurrentHome = Files.createTempDirectory("carbon-concurrent-events-");
        SecurityEventLog concurrent = new SecurityEventLog(concurrentHome, 4_000);
        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            for (int index = 0; index < 50; index++) {
                executor.submit(() -> concurrent.recordBlocked(blocked, evaluation));
            }
        }
        assert concurrent.todayStats().bytesWritten() <= 4_000;
        for (String line : Files.readAllLines(concurrent.blockedPath())) {
            assert io.carbongate.json.Json.object(line).get("type").equals("blocked");
        }
    }
}
