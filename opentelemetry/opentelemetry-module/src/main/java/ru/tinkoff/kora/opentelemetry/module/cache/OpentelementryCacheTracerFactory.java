package ru.tinkoff.kora.opentelemetry.module.cache;

import io.opentelemetry.api.trace.Tracer;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.cache.telemetry.CacheTelemetryArgs;
import ru.tinkoff.kora.cache.telemetry.CacheTracer;
import ru.tinkoff.kora.cache.telemetry.CacheTracerFactory;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.Objects;

public final class OpentelementryCacheTracerFactory implements CacheTracerFactory {

    private final Tracer tracer;

    public OpentelementryCacheTracerFactory(Tracer tracer) {
        this.tracer = tracer;
    }

    @Nullable
    @Override
    public CacheTracer get(TelemetryConfig.TracingConfig tracing, CacheTelemetryArgs args) {
        if (Objects.requireNonNullElse(tracing.enabled(), true)) {
            return new OpentelementryCacheTracer(tracer);
        } else {
            return null;
        }
    }
}
