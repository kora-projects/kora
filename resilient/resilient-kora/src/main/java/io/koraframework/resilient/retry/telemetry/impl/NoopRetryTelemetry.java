package io.koraframework.resilient.retry.telemetry.impl;

import io.koraframework.resilient.retry.telemetry.RetryObservation;
import io.koraframework.resilient.retry.telemetry.RetryTelemetry;

public final class NoopRetryTelemetry implements RetryTelemetry {

    public static final NoopRetryTelemetry INSTANCE = new NoopRetryTelemetry();

    private NoopRetryTelemetry() {}

    @Override
    public RetryObservation observe() {
        return NoopRetryObservation.INSTANCE;
    }
}
