package io.carbongate.authorization;

import io.carbongate.model.Action;
import io.carbongate.model.Decision;
import io.carbongate.model.Evaluation;
import io.carbongate.model.RiskLevel;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

public final class AuthorizationStoreTest {
    public static void run() throws Exception {
        Path home = Files.createTempDirectory("carbon-approvals-");
        AuthorizationStore store = new AuthorizationStore(home);
        Action action = Action.shell("git status", home);
        Evaluation evaluation = new Evaluation("11111111-1111-1111-1111-111111111111",
                Decision.ASK, RiskLevel.LOW, "ask", List.of("test"), "git status", Instant.now());

        String id = store.ensurePending(action, evaluation);
        assert id.equals(evaluation.id());
        assert store.ensurePending(action, evaluation).equals(id);
        assert store.pending().size() == 1;
        assert store.approve(id);
        assert store.pending().isEmpty();
        assert store.consumeApproved(action);
        assert !store.consumeApproved(action);
        assert !store.approve(id);
    }
}
