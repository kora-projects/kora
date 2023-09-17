package ru.tinkoff.kora.common.readiness;

import java.util.concurrent.CompletionStage;

public interface ReadinessProbe {
    /**
     * Perform readiness probe
     *
     * @return Empty future or null if probe succeeds or probe failure
     */
    CompletionStage<ReadinessProbeFailure> probe();
}
