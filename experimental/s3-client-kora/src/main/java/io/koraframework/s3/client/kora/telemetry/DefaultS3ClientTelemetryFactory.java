package io.koraframework.s3.client.kora.telemetry;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import org.jspecify.annotations.Nullable;

public final class DefaultS3ClientTelemetryFactory implements S3ClientTelemetryFactory {

    private static final Tracer NOOP_TRACER = TracerProvider.noop().get("s3-client-kora-telemetry");
    private static final MeterRegistry NOOP_METER_REGISTRY = new CompositeMeterRegistry();

    @Nullable
    private final Tracer tracer;
    @Nullable
    private final MeterRegistry meterRegistry;

    public DefaultS3ClientTelemetryFactory(@Nullable Tracer tracer,
                                           @Nullable MeterRegistry meterRegistry) {
        this.tracer = tracer;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public S3ClientTelemetry get(S3ClientTelemetryConfig config) {
        var tracerEnabled = this.tracer != null && config.tracing().enabled();
        var metricEnabled = this.meterRegistry != null && config.metrics().enabled();
        if (!tracerEnabled && !metricEnabled && !config.logging().enabled()) {
            return NoopS3ClientTelemetry.INSTANCE;
        }

        var tracer = tracerEnabled ? this.tracer : NOOP_TRACER;
        var meterRegistry = metricEnabled ? this.meterRegistry : NOOP_METER_REGISTRY;
        return new DefaultS3ClientTelemetry(config, tracer, meterRegistry);
    }
}
