package io.koraframework.http.server.common.telemetry.impl;

import io.koraframework.http.server.common.HttpServer;
import io.koraframework.http.server.common.telemetry.HttpServerTelemetry;
import io.koraframework.http.server.common.telemetry.HttpServerTelemetryConfig;
import io.koraframework.http.server.common.telemetry.HttpServerTelemetryFactory;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import org.jspecify.annotations.Nullable;
import org.slf4j.LoggerFactory;

public final class DefaultHttpServerTelemetryFactory implements HttpServerTelemetryFactory {
    private static final Tracer NOOP_TRACER = TracerProvider.noop().get("http-server");

    @Nullable
    private final MeterRegistry meterRegistry;
    @Nullable
    private final Tracer tracer;
    @Nullable
    private final DefaultHttpServerBodyConverter bodyLogger;

    public DefaultHttpServerTelemetryFactory(@Nullable MeterRegistry meterRegistry,
                                             @Nullable Tracer tracer,
                                             @Nullable DefaultHttpServerBodyConverter bodyLogger) {
        this.meterRegistry = meterRegistry;
        this.tracer = tracer;
        this.bodyLogger = bodyLogger;
    }

    @Override
    public HttpServerTelemetry get(HttpServerTelemetryConfig config) {
        var traceEnabled = this.tracer != null && config.tracing().enabled();
        var metricEnabled = this.meterRegistry != null && config.metrics().enabled();
        if (!traceEnabled && !metricEnabled && !config.logging().enabled()) {
            return NoopHttpServerTelemetry.INSTANCE;
        }

        final DefaultHttpServerMetrics metrics;
        if (metricEnabled) {
            metrics = new DefaultHttpServerMetrics(this.meterRegistry, config.metrics());
        } else {
            metrics = NoopHttpServerMetrics.INSTANCE;
        }

        final DefaultHttpServerLogger logger;
        if (config.logging().enabled()) {
            var requestLog = LoggerFactory.getLogger(HttpServer.class.getCanonicalName() + ".request");
            var responseLog = LoggerFactory.getLogger(HttpServer.class.getCanonicalName() + ".response");
            logger = new DefaultHttpServerLogger(requestLog, responseLog, this.bodyLogger != null ? this.bodyLogger : new DefaultHttpServerBodyConverter(), config.logging());
        } else {
            logger = NoopHttpServerLogger.INSTANCE;
        }

        var tracer = traceEnabled ? this.tracer : NOOP_TRACER;
        return new DefaultHttpServerTelemetry(config, tracer, logger, metrics);
    }
}
