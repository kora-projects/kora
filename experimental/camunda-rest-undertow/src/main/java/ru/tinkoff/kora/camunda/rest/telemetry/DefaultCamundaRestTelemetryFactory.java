package ru.tinkoff.kora.camunda.rest.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.camunda.rest.CamundaRestConfig;

public final class DefaultCamundaRestTelemetryFactory implements CamundaRestTelemetryFactory {

    private static final CamundaRestTelemetry.CamundaRestTelemetryContext EMPTY_CTX = (_, _, _, _) -> {};
    private static final CamundaRestTelemetry EMPTY = (_, _, _, _, _, _, _) -> EMPTY_CTX;

    @Nullable
    private final CamundaRestLoggerFactory logger;
    @Nullable
    private final CamundaRestMetricsFactory metrics;
    @Nullable
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
        if (metrics == null && logging == null && tracer == null) {
            return EMPTY;
        }

        return new DefaultCamundaRestTelemetry(metrics, logging, tracer);
    }
}
