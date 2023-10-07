package ru.tinkoff.kora.common.readiness;

import jakarta.annotation.Nullable;

public interface ReadinessProbe {
    /**
     * Perform readiness probe
     *
     * @return null if probe succeeds or probe failure
     */
    @Nullable
    ReadinessProbeFailure probe() throws Exception;
}
