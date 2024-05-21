package ru.tinkoff.kora.camunda.rest.telemetry;

import io.undertow.util.HeaderMap;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.camunda.rest.CamundaRestConfig;

public final class DefaultCamundaRestTelemetryFactory implements CamundaRestTelemetryFactory {

    private static final CamundaRestTelemetry EMPTY_TELEMETRY = new StubCamundaRestTelemetry();
    private static final CamundaRestTelemetry.CamundaRestTelemetryContext EMPTY_CONTEXT = new StubCamundaRestTelemetryContext();

    private final CamundaRestLoggerFactory logger;
    private final CamundaRestMetricsFactory metrics;
    private final CamundaRestTracerFactory tracer;

    public DefaultCamundaRestTelemetryFactory(@Nullable CamundaRestLoggerFactory logger,
                                              @Nullable CamundaRestMetricsFactory metrics,
                                              @Nullable CamundaRestTracerFactory tracer) {
        this.logger = logger;
        this.metrics = metrics;
        this.tracer = tracer;
    }

    @Override
    public CamundaRestTelemetry get(CamundaRestConfig.CamundaRestTelemetryConfig config) {
        var metrics = this.metrics == null ? null : this.metrics.get(config.metrics());
        var logging = this.logger == null ? null : this.logger.get(config.logging());
        var tracer = this.tracer == null ? null : this.tracer.get(config.tracing());
        if (metrics == null && tracer == null && logger == null) {
            return EMPTY_TELEMETRY;
        }

        return new DefaultCamundaRestTelemetry(metrics, logging, tracer);
    }

    private static final class StubCamundaRestTelemetry implements CamundaRestTelemetry {

        @Override
        public CamundaRestTelemetryContext get(String method, String path, HeaderMap headerMap) {
            return EMPTY_CONTEXT;
        }
    }

    private static final class StubCamundaRestTelemetryContext implements CamundaRestTelemetry.CamundaRestTelemetryContext {

        @Override
        public void close(int statusCode, @Nullable Throwable exception) {
            // do nothing
        }
    }
}
