package io.koraframework.openfeature.telemetry.impl;

import dev.openfeature.sdk.HookContext;
import io.koraframework.openfeature.telemetry.OpenfeatureObservation;
import io.koraframework.openfeature.telemetry.OpenfeatureTelemetry;

public final class NoopOpenfeatureTelemetry implements OpenfeatureTelemetry {

    public static final NoopOpenfeatureTelemetry INSTANCE = new NoopOpenfeatureTelemetry();

    private NoopOpenfeatureTelemetry() {}

    @Override
    public OpenfeatureObservation observe(HookContext<?> evaluation) {
        return NoopOpenfeatureObservation.INSTANCE;
    }
}
