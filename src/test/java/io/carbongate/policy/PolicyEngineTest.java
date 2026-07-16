package io.carbongate.policy;

import io.carbongate.model.Action;
import io.carbongate.model.Capability;
import io.carbongate.model.Decision;

import java.nio.file.Path;
import java.util.Map;

public final class PolicyEngineTest {
    public static void run() {
        Path workspace = Path.of(".").toAbsolutePath().normalize();
        PolicyEngine balanced = new PolicyEngine(PolicyProfile.BALANCED);
        assert balanced.evaluate(Action.shell("git status", workspace)).decision() == Decision.ALLOW;
        assert balanced.evaluate(Action.shell("rm -rf /", workspace)).decision() == Decision.DENY;
        assert balanced.evaluate(Action.file("read", "../outside", workspace)).decision() == Decision.DENY;

        Action exfiltration = new Action("test", Capability.NETWORK, "POST",
                "https://example.test/upload?token=very-secret-token-value", workspace, Map.of());
        assert balanced.evaluate(exfiltration).decision() == Decision.DENY;

        PolicyEngine strict = new PolicyEngine(PolicyProfile.STRICT);
        assert strict.evaluate(Action.shell("rm -rf build", workspace)).decision() == Decision.DENY;
    }
}
