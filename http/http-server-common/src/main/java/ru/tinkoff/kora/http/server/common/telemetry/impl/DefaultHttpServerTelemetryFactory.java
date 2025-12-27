package ru.tinkoff.kora.http.server.common.telemetry.impl;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import org.jspecify.annotations.Nullable;
import ru.tinkoff.kora.http.server.common.telemetry.HttpServerTelemetry;
import ru.tinkoff.kora.http.server.common.telemetry.HttpServerTelemetryConfig;
import ru.tinkoff.kora.http.server.common.telemetry.HttpServerTelemetryFactory;
import ru.tinkoff.kora.http.server.common.telemetry.NoopHttpServerTelemetry;

public final class DefaultHttpServerTelemetryFactory implements HttpServerTelemetryFactory {
    private final MeterRegistry meterRegistry;
    private final Tracer tracer;

    public DefaultHttpServerTelemetryFactory(@Nullable MeterRegistry meterRegistry, @Nullable Tracer tracer) {
        if (meterRegistry == null) {
            meterRegistry = new CompositeMeterRegistry();
        }
        if (tracer == null) {
            tracer = TracerProvider.noop().get("http-server");
        }
        this.meterRegistry = meterRegistry;
        this.tracer = tracer;
    }

    @Override
    public HttpServerTelemetry get(HttpServerTelemetryConfig config) {
        if (!config.logging().enabled() && !config.metrics().enabled() && !config.tracing().enabled()) {
            return NoopHttpServerTelemetry.INSTANCE;
        }

        return new DefaultHttpServerTelemetry(config, meterRegistry, tracer);
    }
}
