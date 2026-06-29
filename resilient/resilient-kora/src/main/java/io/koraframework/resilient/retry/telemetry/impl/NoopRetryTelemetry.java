package io.koraframework.resilient.retry.telemetry.impl;

import io.koraframework.resilient.retry.*;
import io.koraframework.resilient.retry.telemetry.*;

public final class NoopRetryTelemetry implements RetryTelemetry {

    public static final NoopRetryTelemetry INSTANCE = new NoopRetryTelemetry();

    private NoopRetryTelemetry() {}

    @Override
    public RetryObservation observe() {
        return NoopRetryObservation.INSTANCE;
    }
}
