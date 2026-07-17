package io.carbongate.audit;

import io.carbongate.model.Action;
import io.carbongate.model.Evaluation;

import java.util.Map;

public interface AuditSink {
    boolean recordDecision(Action action, Evaluation evaluation);

    boolean recordError(String component, String message);

    boolean recordControl(String type, Map<String, Object> details);

    default boolean requiresSuccessfulDecisionRecord() {
        return false;
    }
}
