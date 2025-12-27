package ru.tinkoff.kora.http.client.common.telemetry.impl;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import org.jspecify.annotations.Nullable;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.http.client.common.telemetry.HttpClientTelemetry;
import ru.tinkoff.kora.http.client.common.telemetry.HttpClientTelemetryConfig;
import ru.tinkoff.kora.http.client.common.telemetry.HttpClientTelemetryFactory;

public final class DefaultHttpClientTelemetryFactory implements HttpClientTelemetryFactory {
    @Nullable
    private final Tracer tracer;
    @Nullable
    private final MeterRegistry meterRegistry;

    public DefaultHttpClientTelemetryFactory(@Nullable Tracer tracer, @Nullable MeterRegistry meterRegistry) {
        this.tracer = tracer;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public HttpClientTelemetry get(HttpClientTelemetryConfig config, String clientName) {
        if (!config.metrics().enabled() && !config.tracing().enabled() && !config.logging().enabled()) {
            return NoopHttpClientTelemetry.INSTANCE;
        }
        var requestLog = LoggerFactory.getLogger(clientName + ".request");
        var responseLog = LoggerFactory.getLogger(clientName + ".response");
        var tracer = this.tracer;
        if (tracer == null || !config.tracing().enabled()) {
            tracer = TracerProvider.noop().get("http-client");
        }
        var meterRegistry = this.meterRegistry;
        if (meterRegistry == null || !config.metrics().enabled()) {
            meterRegistry = new CompositeMeterRegistry();
        }

        return new DefaultHttpClientTelemetry(
            config, tracer, new DefaultHttpClientLogger(requestLog, responseLog, config.logging()), new DefaultHttpClientMetrics(meterRegistry, config.metrics())
        );
    }
}
