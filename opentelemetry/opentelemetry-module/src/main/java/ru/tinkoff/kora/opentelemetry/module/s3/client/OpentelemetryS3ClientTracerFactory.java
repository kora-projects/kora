package ru.tinkoff.kora.opentelemetry.module.s3.client;

import io.opentelemetry.api.trace.Tracer;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.Objects;

public final class OpentelemetryS3ClientTracerFactory implements S3ClientTracerFactory {

    private final Tracer tracer;

    public OpentelemetryS3ClientTracerFactory(Tracer tracer) {
        this.tracer = tracer;
    }

    @Nullable
    @Override
    public S3ClientTracer get(TelemetryConfig.TracingConfig config, Class<?> client) {
        if (Objects.requireNonNullElse(config.enabled(), true)) {
            return new OpentelemetryS3ClientTracer(client, tracer);
        } else {
            return null;
        }
    }
}
