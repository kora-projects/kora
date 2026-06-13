package io.koraframework.bpmn.operaton.rest.telemetry.impl;

import io.koraframework.bpmn.operaton.rest.telemetry.OperatonRestObservation;
import io.koraframework.bpmn.operaton.rest.telemetry.OperatonRestTelemetry;
import io.koraframework.http.server.common.telemetry.HttpServerTelemetryConfig;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.semconv.HttpAttributes;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.UrlAttributes;
import io.undertow.server.HttpServerExchange;
import org.jspecify.annotations.Nullable;

public class DefaultOperatonRestTelemetry implements OperatonRestTelemetry {

    public record TelemetryContext(HttpServerTelemetryConfig config,
                                   boolean isTraceEnabled,
                                   boolean isMetricsEnabled,
                                   Tracer tracer,
                                   MeterRegistry meterRegistry) {}

    protected final TelemetryContext context;
    protected final DefaultOperatonRestLoggerFactory.DefaultOperatonRestLogger logger;
    protected final DefaultOperatonRestMetricsFactory.DefaultOperatonRestMetrics metrics;

    public DefaultOperatonRestTelemetry(HttpServerTelemetryConfig config,
                                        boolean isTraceEnabled,
                                        boolean isMetricsEnabled,
                                        Tracer tracer,
                                        MeterRegistry meterRegistry,
                                        DefaultOperatonRestLoggerFactory loggerFactory,
                                        DefaultOperatonRestMetricsFactory metricsFactory) {
        this.context = new TelemetryContext(config, isTraceEnabled, isMetricsEnabled, tracer, meterRegistry);
        this.logger = loggerFactory.create(this.context);
        this.metrics = metricsFactory.create(this.context);
    }

    @Override
    public OperatonRestObservation observe(HttpServerExchange exchange, @Nullable String route) {
        var span = this.createSpan(route, exchange);
        return new DefaultOperatonRestObservation(exchange, this.context, exchange.getRequestStartTime(), span, logger, metrics);
    }

    protected Span createSpan(String template, HttpServerExchange exchange) {
        if (template == null || !this.context.isTraceEnabled()) {
            return Span.getInvalid();
        }
        var span = this.context.tracer()
            .spanBuilder(exchange.getRequestMethod() + " " + template)
            .setSpanKind(SpanKind.SERVER)
            .setParent(io.opentelemetry.context.Context.current())
            .setAttribute(HttpAttributes.HTTP_REQUEST_METHOD, exchange.getRequestMethod().toString())
            .setAttribute(UrlAttributes.URL_SCHEME, exchange.getRequestScheme())
            .setAttribute(ServerAttributes.SERVER_ADDRESS, exchange.getHostAndPort())
            .setAttribute(UrlAttributes.URL_PATH, exchange.getRequestPath())
            .setAttribute(HttpAttributes.HTTP_ROUTE, template);
        for (var attribute : this.context.config().tracing().attributes().entrySet()) {
            span.setAttribute(attribute.getKey(), attribute.getValue());
        }
        return span.startSpan();
    }

}
