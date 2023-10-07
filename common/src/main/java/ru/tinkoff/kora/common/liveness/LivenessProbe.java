package ru.tinkoff.kora.common.liveness;

public interface LivenessProbe {
    /**
     * Perform liveness probe
     *
     * @return null if probe succeeds or probe failure
     */
    LivenessProbeFailure probe() throws Exception;
}
