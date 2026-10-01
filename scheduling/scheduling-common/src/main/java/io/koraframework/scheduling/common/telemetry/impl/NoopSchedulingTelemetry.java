package io.koraframework.scheduling.common.telemetry.impl;

import io.koraframework.scheduling.common.telemetry.SchedulingObservation;
import io.koraframework.scheduling.common.telemetry.SchedulingTelemetry;

public final class NoopSchedulingTelemetry implements SchedulingTelemetry {

    public static final NoopSchedulingTelemetry INSTANCE = new NoopSchedulingTelemetry(Void.class, "noop");

    private final Class<?> jobClass;
    private final String jobMethod;

    public NoopSchedulingTelemetry(Class<?> jobClass, String jobMethod) {
        this.jobClass = jobClass;
        this.jobMethod = jobMethod;
    }

    @Override
    public Class<?> jobClass() {
        return this.jobClass;
    }

    @Override
    public String jobMethod() {
        return this.jobMethod;
    }

    @Override
    public SchedulingObservation observe() {
        return NoopSchedulingObservation.INSTANCE;
    }
}
