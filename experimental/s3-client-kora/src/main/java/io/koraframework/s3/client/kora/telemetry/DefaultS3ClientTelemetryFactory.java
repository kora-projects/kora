package io.koraframework.s3.client.kora.telemetry;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import org.jspecify.annotations.Nullable;

public class DefaultS3ClientTelemetryFactory implements S3ClientTelemetryFactory {
    @Nullable
    private final Tracer tracer;
    @Nullable
    private final MeterRegistry meterRegistry;

    public DefaultS3ClientTelemetryFactory(@Nullable Tracer tracer, @Nullable MeterRegistry meterRegistry) {
        this.tracer = tracer;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public S3ClientTelemetry get(S3ClientTelemetryConfig config) {
        var tracerEnabled = this.tracer != null && config.tracing().enabled();
        var metricEnabled = this.meterRegistry != null && config.metrics().enabled();
        if (!config.logging().enabled() && !metricEnabled && !tracerEnabled) {
            return NoopS3ClientTelemetry.INSTANCE;
        }

        var tracer = !tracerEnabled ? TracerProvider.noop().get("s3-kora-client-telemetry") : this.tracer;
        var meterRegistry = !metricEnabled ? new CompositeMeterRegistry() : this.meterRegistry;
        return new DefaultS3ClientTelemetry(config, tracer, meterRegistry);
    }
}
