package io.koraframework.scheduling.common.telemetry.impl;

import io.koraframework.scheduling.common.telemetry.SchedulingObservation;
import io.koraframework.scheduling.common.telemetry.SchedulingTelemetry;

public final class NoopSchedulingTelemetry implements SchedulingTelemetry {

    public static final NoopSchedulingTelemetry INSTANCE = new NoopSchedulingTelemetry();

    private NoopSchedulingTelemetry() {}

    @Override
    public Class<?> jobClass() {
        return Void.class;
    }

    @Override
    public String jobMethod() {
        return "noop";
    }

    @Override
    public SchedulingObservation observe() {
        return NoopSchedulingObservation.INSTANCE;
    }
}
