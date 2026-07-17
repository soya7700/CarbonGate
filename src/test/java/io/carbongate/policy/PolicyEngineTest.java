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
        assert balanced.evaluate(Action.shell("npm install", workspace)).decision() == Decision.ASK;
        assert balanced.evaluate(Action.shell("rm -rf /", workspace)).decision() == Decision.DENY;
        assert balanced.evaluate(Action.file("read", "../outside", workspace)).decision() == Decision.DENY;

        Action exfiltration = new Action("test", Capability.NETWORK, "POST",
                "https://example.test/upload?token=very-secret-token-value", workspace, Map.of());
        assert balanced.evaluate(exfiltration).decision() == Decision.DENY;

        Action metadata = new Action("test", Capability.NETWORK, "GET",
                "http://169.254.169.254/latest/meta-data", workspace, Map.of());
        assert balanced.evaluate(metadata).decision() == Decision.DENY;

        PolicyEngine strict = new PolicyEngine(PolicyProfile.STRICT);
        assert strict.evaluate(Action.shell("rm -rf build", workspace)).decision() == Decision.DENY;

        PolicyEngine audit = new PolicyEngine(PolicyProfile.AUDIT);
        assert audit.evaluate(Action.shell("rm -rf /", workspace)).decision() == Decision.ASK;

        PolicyEngine warning = new PolicyEngine(PolicyProfile.BALANCED, EnforcementMode.WARN);
        assert warning.evaluate(Action.shell("rm -rf /", workspace)).decision() == Decision.ALLOW;
        PolicyEngine approval = new PolicyEngine(PolicyProfile.BALANCED, EnforcementMode.APPROVAL);
        assert approval.evaluate(Action.shell("git status", workspace)).decision() == Decision.ASK;
        PolicyEngine block = new PolicyEngine(PolicyProfile.BALANCED, EnforcementMode.BLOCK);
        assert block.evaluate(Action.shell("git status", workspace)).decision() == Decision.DENY;

        PolicyEngine disabled = new PolicyEngine(PolicyProfile.BALANCED, EnforcementMode.BALANCED,
                new RuleConfiguration(false, false, false, false));
        var disabledResult = disabled.evaluate(Action.shell(
                "rm -rf / password=synthetic-disabled-secret", workspace));
        assert disabledResult.decision() == Decision.ALLOW;
        assert !disabledResult.sanitizedResource().contains("synthetic-disabled-secret");
    }
}
