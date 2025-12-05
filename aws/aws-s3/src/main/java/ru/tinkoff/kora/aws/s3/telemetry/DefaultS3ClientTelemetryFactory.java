package ru.tinkoff.kora.aws.s3.telemetry;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.aws.s3.S3Config;

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
    public S3ClientTelemetry get(S3Config s3Config) {
        var telemetryConfig = s3Config.telemetry();
        if (!telemetryConfig.logging().enabled() && !telemetryConfig.metrics().enabled() && !telemetryConfig.tracing().enabled()) {
            return NoopS3ClientTelemetry.INSTANCE;
        }
        var tracer = this.tracer == null || !telemetryConfig.tracing().enabled()
            ? TracerProvider.noop().get("s3-client-telemetry")
            : this.tracer;
        var meterRegistry = this.meterRegistry == null || !telemetryConfig.metrics().enabled()
            ? new CompositeMeterRegistry()
            : this.meterRegistry;

        return new DefaultS3ClientTelemetry(s3Config, tracer, meterRegistry);
    }
}
