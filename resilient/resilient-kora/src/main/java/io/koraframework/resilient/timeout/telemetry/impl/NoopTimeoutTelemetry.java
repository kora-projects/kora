package io.koraframework.resilient.timeout.telemetry.impl;

import io.koraframework.resilient.timeout.telemetry.TimeoutObservation;
import io.koraframework.resilient.timeout.telemetry.TimeoutTelemetry;

import java.time.Duration;

public final class NoopTimeoutTelemetry implements TimeoutTelemetry {

    public static final NoopTimeoutTelemetry INSTANCE = new NoopTimeoutTelemetry();

    private NoopTimeoutTelemetry() {}

    @Override
    public TimeoutObservation observe(Duration timeToWait) {
        return NoopTimeoutObservation.INSTANCE;
    }
}
