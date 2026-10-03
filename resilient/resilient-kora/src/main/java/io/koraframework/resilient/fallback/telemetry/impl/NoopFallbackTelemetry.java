package io.koraframework.resilient.fallback.telemetry.impl;

import io.koraframework.resilient.fallback.telemetry.FallbackObservation;
import io.koraframework.resilient.fallback.telemetry.FallbackTelemetry;

public final class NoopFallbackTelemetry implements FallbackTelemetry {

    public static final NoopFallbackTelemetry INSTANCE = new NoopFallbackTelemetry();

    private NoopFallbackTelemetry() {}

    @Override
    public FallbackObservation observe() {
        return NoopFallbackObservation.INSTANCE;
    }
}
