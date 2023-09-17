package ru.tinkoff.kora.common.liveness;

import java.util.concurrent.CompletionStage;

public interface LivenessProbe {
    /**
     * Perform liveness probe
     *
     * @return empty future if probe succeeds or probe failure
     */
    CompletionStage<LivenessProbeFailure> probe();
}
