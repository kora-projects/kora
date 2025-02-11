package ru.tinkoff.kora.opentelemetry.module.s3.client;

import io.opentelemetry.api.trace.Tracer;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.Objects;

public final class OpentelemetryS3TracerFactory implements S3KoraClientTracerFactory {

    private final Tracer tracer;

    public OpentelemetryS3TracerFactory(Tracer tracer) {
        this.tracer = tracer;
    }

    @Nullable
    @Override
    public S3KoraClientTracer get(TelemetryConfig.TracingConfig config, Class<?> client) {
        if (Objects.requireNonNullElse(config.enabled(), true)) {
            return new OpentelemetryS3Tracer(client, tracer);
        } else {
            return null;
        }
    }
}
