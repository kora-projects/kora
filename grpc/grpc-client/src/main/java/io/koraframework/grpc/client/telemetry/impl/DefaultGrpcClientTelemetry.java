package io.koraframework.grpc.client.telemetry.impl;

import io.grpc.CallOptions;
import io.grpc.MethodDescriptor;
import io.grpc.ServiceDescriptor;
import io.koraframework.grpc.client.telemetry.*;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.incubating.RpcIncubatingAttributes;

import java.net.URI;
import java.util.Objects;

public class DefaultGrpcClientTelemetry implements GrpcClientTelemetry {

    public record TelemetryContext(GrpcClientTelemetryConfig config,
                                   ServiceDescriptor service,
                                   URI uri,
                                   boolean isTraceEnabled,
                                   boolean isMetricsEnabled,
                                   MeterRegistry meterRegistry,
                                   Tracer tracer) {

        public static final TelemetryContext EMPTY = new TelemetryContext(
            new $GrpcClientTelemetryConfig_ConfigValueExtractor.GrpcClientTelemetryConfig_Impl(
                new $GrpcClientTelemetryConfig_GrpcClientLoggingConfig_ConfigValueExtractor.GrpcClientLoggingConfig_Defaults(),
                new $GrpcClientTelemetryConfig_GrpcClientMetricsConfig_ConfigValueExtractor.GrpcClientMetricsConfig_Defaults(),
                new $GrpcClientTelemetryConfig_GrpcClientTracingConfig_ConfigValueExtractor.GrpcClientTracingConfig_Defaults()
            ),
            new ServiceDescriptor("none"),
            URI.create("http://localhost"),
            false,
            false,
            DefaultGrpcClientTelemetryFactory.NOOP_METER_REGISTRY,
            DefaultGrpcClientTelemetryFactory.NOOP_TRACER
        );
    }

    protected final TelemetryContext context;
    protected final DefaultGrpcClientLoggerFactory.DefaultGrpcClientLogger logger;
    protected final DefaultGrpcClientMetricsFactory.DefaultGrpcClientMetrics metrics;

    public DefaultGrpcClientTelemetry(GrpcClientTelemetryConfig config,
                                      Tracer tracer,
                                      MeterRegistry meterRegistry,
                                      DefaultGrpcClientMetricsFactory metricsFactory,
                                      DefaultGrpcClientLoggerFactory loggerFactory,
                                      ServiceDescriptor service,
                                      URI uri) {
        var isTraceEnabled = config.tracing().enabled() && tracer != DefaultGrpcClientTelemetryFactory.NOOP_TRACER;
        var isMetricsEnabled = config.metrics().enabled() && meterRegistry != DefaultGrpcClientTelemetryFactory.NOOP_METER_REGISTRY;
        this.context = new TelemetryContext(config, service, uri, isTraceEnabled, isMetricsEnabled, meterRegistry, tracer);
        this.logger = loggerFactory.create(this.context);
        this.metrics = metricsFactory.create(this.context);
    }

    @Override
    public <ReqT, RespT> GrpcClientObservation observe(MethodDescriptor<ReqT, RespT> method, CallOptions callOptions) {
        var span = this.createSpan(method);
        return new DefaultGrpcClientObservation(method, context, span, logger, metrics);
    }

    protected Span createSpan(MethodDescriptor<?, ?> method) {
        if (!this.context.isTraceEnabled()) {
            return Span.getInvalid();
        }
        var methodName = Objects.requireNonNullElse(method.getBareMethodName(), "unknownMethod");
        var span = this.context.tracer().spanBuilder(method.getFullMethodName())
            .setAttribute(RpcIncubatingAttributes.RPC_METHOD, methodName)
            .setAttribute(RpcIncubatingAttributes.RPC_SERVICE, this.context.service().getName())
            .setAttribute(RpcIncubatingAttributes.RPC_SYSTEM, "grpc")
            .setAttribute(ServerAttributes.SERVER_ADDRESS, this.context.uri().getHost())
            .setAttribute(ServerAttributes.SERVER_PORT, this.context.uri().getPort())
            .setSpanKind(SpanKind.CLIENT);
        for (var entry : this.context.config().tracing().attributes().entrySet()) {
            span.setAttribute(entry.getKey(), entry.getValue());
        }

        return span.startSpan();
    }
}
