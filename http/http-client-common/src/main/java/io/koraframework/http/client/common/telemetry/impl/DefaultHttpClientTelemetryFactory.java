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

public final class DefaultHttpClientTelemetryFactory implements HttpClientTelemetryFactory {

    private static final Tracer NOOP_TRACER = TracerProvider.noop().get("http-client");
    private static final MeterRegistry NOOP_METER_REGISTRY = new CompositeMeterRegistry();

    @Nullable
    private final Tracer tracer;
    @Nullable
    private final MeterRegistry meterRegistry;
    @Nullable
    private final DefaultHttpClientBodyConverter bodyLogger;

    public DefaultHttpClientTelemetryFactory(@Nullable Tracer tracer,
                                             @Nullable MeterRegistry meterRegistry,
                                             @Nullable DefaultHttpClientBodyConverter bodyLogger) {
        this.tracer = tracer;
        this.meterRegistry = meterRegistry;
        this.bodyLogger = bodyLogger;
    }

    @Override
    public HttpClientTelemetry get(String clientConfigPath, String clientCanonicalName, HttpClientTelemetryConfig config) {
        var traceEnabled = this.tracer != null && config.tracing().enabled();
        var metricEnabled = this.meterRegistry != null && config.metrics().enabled();
        if (!traceEnabled && !metricEnabled && !config.logging().enabled()) {
            return NoopHttpClientTelemetry.INSTANCE;
        }

        final DefaultHttpClientMetrics metrics;
        if (metricEnabled) {
            metrics = new DefaultHttpClientMetrics(clientConfigPath, clientCanonicalName, this.meterRegistry, config.metrics());
        } else {
            metrics = NoopHttpClientMetrics.INSTANCE;
        }

        final DefaultHttpClientLogger logger;
        if (config.logging().enabled()) {
            var requestLog = LoggerFactory.getLogger(clientCanonicalName + ".request");
            var responseLog = LoggerFactory.getLogger(clientCanonicalName + ".response");
            logger = new DefaultHttpClientLogger(clientConfigPath, clientCanonicalName, requestLog, responseLog, (this.bodyLogger != null) ? this.bodyLogger : new DefaultHttpClientBodyConverter(), config.logging());
        } else {
            logger = NoopHttpClientLogger.INSTANCE;
        }

        var tracer = traceEnabled ? this.tracer : NOOP_TRACER;
        return new DefaultHttpClientTelemetry(clientConfigPath, clientCanonicalName, config, tracer, logger, metrics);
    }
}
