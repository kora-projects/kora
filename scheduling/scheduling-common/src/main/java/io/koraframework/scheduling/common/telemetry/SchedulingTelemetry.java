package io.koraframework.scheduling.common.telemetry;

public interface SchedulingTelemetry {

    Class<?> jobClass();

    String jobMethod();

    SchedulingObservation observe();

    /**
     * Whether a failed execution is logged by the {@link SchedulingObservation} itself,
     * so a scheduler that swallows job exceptions does not need to log them again.
     */
    default boolean isLoggingEnabled() {
        return false;
    }
}
