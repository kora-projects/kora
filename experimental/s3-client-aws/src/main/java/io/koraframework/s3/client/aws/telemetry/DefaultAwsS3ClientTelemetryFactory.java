package io.koraframework.s3.client.aws.telemetry;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import org.jspecify.annotations.Nullable;

public class DefaultAwsS3ClientTelemetryFactory implements AwsS3ClientTelemetryFactory {
    @Nullable
    private final Tracer tracer;
    @Nullable
    private final MeterRegistry meterRegistry;

    public DefaultAwsS3ClientTelemetryFactory(@Nullable Tracer tracer, @Nullable MeterRegistry meterRegistry) {
        this.tracer = tracer;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public AwsS3ClientTelemetry get(AwsS3ClientTelemetryConfig config) {
        var tracerEnabled = this.tracer != null && config.tracing().enabled();
        var metricEnabled = this.meterRegistry != null && config.metrics().enabled();
        if (!config.logging().enabled() && !metricEnabled && !tracerEnabled) {
            return NoopAwsS3ClientTelemetry.INSTANCE;
        }

        var tracer = !tracerEnabled ? TracerProvider.noop().get("s3-aws-client-telemetry") : this.tracer;
        var meterRegistry = !metricEnabled ? new CompositeMeterRegistry() : this.meterRegistry;
        return new DefaultAwsS3ClientTelemetry(config, tracer, meterRegistry);
    }
}
