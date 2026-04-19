package io.koraframework.http.client.common.telemetry.impl;

import io.koraframework.http.client.common.telemetry.HttpClientTelemetry;
import io.koraframework.http.client.common.telemetry.HttpClientTelemetryConfig;
import io.koraframework.http.client.common.telemetry.HttpClientTelemetryFactory;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import org.jspecify.annotations.Nullable;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.NOPLogger;

public final class DefaultHttpClientTelemetryFactory implements HttpClientTelemetryFactory {

    private static final Tracer NOOP_TRACER = TracerProvider.noop().get("http-client");
    private static final MeterRegistry NOOP_METER_REGISTRY = new CompositeMeterRegistry();

    @Nullable
    private final Tracer tracer;
    @Nullable
    private final MeterRegistry meterRegistry;
    @Nullable
    private final DefaultHttpClientBodyLogger bodyLogger;

    public DefaultHttpClientTelemetryFactory(@Nullable Tracer tracer,
                                             @Nullable MeterRegistry meterRegistry,
                                             @Nullable DefaultHttpClientBodyLogger bodyLogger) {
        this.tracer = tracer;
        this.meterRegistry = meterRegistry;
        this.bodyLogger = bodyLogger;
    }

    @Override
    public HttpClientTelemetry get(String clientName, String clientImpl, HttpClientTelemetryConfig config) {
        var traceEnabled = this.tracer != null && config.tracing().enabled();
        var metricEnabled = this.meterRegistry != null && config.metrics().enabled();
        if (!traceEnabled && !metricEnabled && !config.logging().enabled()) {
            return NoopHttpClientTelemetry.INSTANCE;
        }

        var meterRegistry = metricEnabled ? this.meterRegistry : NOOP_METER_REGISTRY;
        var metrics = (metricEnabled)
            ? new DefaultHttpClientMetrics(clientName, clientImpl, meterRegistry, config.metrics())
            : NoopHttpClientMetrics.INSTANCE;

        var requestLog = config.logging().enabled()
            ? LoggerFactory.getLogger(clientImpl + ".request")
            : NOPLogger.NOP_LOGGER;
        var responseLog = config.logging().enabled()
            ? LoggerFactory.getLogger(clientImpl + ".response")
            : NOPLogger.NOP_LOGGER;

        var logger = (config.logging().enabled())
            ? new DefaultHttpClientLogger(clientName, clientImpl, requestLog, responseLog, (this.bodyLogger != null) ? this.bodyLogger : new DefaultHttpClientBodyLogger(), config.logging())
            : NoopHttpClientLogger.INSTANCE;

        var tracer = traceEnabled ? this.tracer : NOOP_TRACER;
        return new DefaultHttpClientTelemetry(clientName, clientImpl, config, tracer, logger, metrics);
    }
}
