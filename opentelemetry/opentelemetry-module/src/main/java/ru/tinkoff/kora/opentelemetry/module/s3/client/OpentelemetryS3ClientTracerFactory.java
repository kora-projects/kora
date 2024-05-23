package ru.tinkoff.kora.opentelemetry.module.s3.client;

import io.opentelemetry.api.trace.Tracer;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.s3.client.telemetry.S3ClientTracer;
import ru.tinkoff.kora.s3.client.telemetry.S3ClientTracerFactory;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.Objects;

public final class OpentelemetryS3ClientTracerFactory implements S3ClientTracerFactory {

    private final Tracer tracer;

    public OpentelemetryS3ClientTracerFactory(Tracer tracer) {
        this.tracer = tracer;
    }

    @Nullable
    @Override
    public S3ClientTracer get(TelemetryConfig.TracingConfig config, String clientName) {
        if (Objects.requireNonNullElse(config.enabled(), true)) {
            return new OpentelemetryS3ClientTracer(clientName, tracer);
        } else {
            return null;
        }
    }
}
