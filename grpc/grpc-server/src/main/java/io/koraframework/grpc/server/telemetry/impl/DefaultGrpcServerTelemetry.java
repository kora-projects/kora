package io.koraframework.grpc.server.telemetry.impl;

import io.grpc.Grpc;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.koraframework.grpc.server.telemetry.*;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.NetworkAttributes;
import io.opentelemetry.semconv.incubating.RpcIncubatingAttributes;

public class DefaultGrpcServerTelemetry implements GrpcServerTelemetry {

    public record TelemetryContext(String name,
                                   int port,
                                   GrpcServerTelemetryConfig config,
                                   boolean isTracingEnabled,
                                   boolean isMetricsEnabled,
                                   MeterRegistry meterRegistry,
                                   Tracer tracer,
                                   DefaultGrpcServerBodyConverter bodyConverter) {

        public static final TelemetryContext EMPTY = new TelemetryContext(
            "NONE",
            -1,
            new $GrpcServerTelemetryConfig_ConfigValueMapper.GrpcServerTelemetryConfig_Impl(
                new $GrpcServerTelemetryConfig_GrpcServerLoggingConfig_ConfigValueMapper.GrpcServerLoggingConfig_Defaults(),
                new $GrpcServerTelemetryConfig_GrpcServerMetricsConfig_ConfigValueMapper.GrpcServerMetricsConfig_Defaults(),
                new $GrpcServerTelemetryConfig_GrpcServerTracingConfig_ConfigValueMapper.GrpcServerTracingConfig_Defaults()
            ),
            false,
            false,
            DefaultGrpcServerTelemetryFactory.NOOP_METER_REGISTRY,
            DefaultGrpcServerTelemetryFactory.NOOP_TRACER,
            new DefaultGrpcServerBodyConverter()
        );
    }

    protected final TelemetryContext context;
    protected final DefaultGrpcServerLoggerFactory.DefaultGrpcServerLogger logger;
    protected final DefaultGrpcServerMetricsFactory.DefaultGrpcServerMetrics metrics;

    public DefaultGrpcServerTelemetry(String name,
                                      int port,
                                      GrpcServerTelemetryConfig config,
                                      Tracer tracer,
                                      MeterRegistry meterRegistry,
                                      DefaultGrpcServerMetricsFactory metricsFactory,
                                      DefaultGrpcServerLoggerFactory loggerFactory,
                                      DefaultGrpcServerBodyConverter bodyConverter) {
        var isTracingEnabled = config.tracing().enabled() && tracer != DefaultGrpcServerTelemetryFactory.NOOP_TRACER;
        var isMetricsEnabled = config.metrics().enabled() && meterRegistry != DefaultGrpcServerTelemetryFactory.NOOP_METER_REGISTRY;
        this.context = new TelemetryContext(name, port, config, isTracingEnabled, isMetricsEnabled, meterRegistry, tracer, bodyConverter);
        this.logger = loggerFactory.create(this.context);
        this.metrics = metricsFactory.create(this.context);
    }

    @Override
    public GrpcServerObservation observe(ServerCall<?, ?> call, Metadata headers) {
        var service = this.service(call);
        var method = this.method(call);
        var span = this.createSpan(call, headers, service, method);
        return new DefaultGrpcServerObservation(context, service, method, headers, span, logger, metrics);
    }

    protected Span createSpan(ServerCall<?, ?> call, Metadata headers, String serviceName, String methodName) {
        if (!this.context.isTracingEnabled()) {
            return Span.getInvalid();
        }
        var remoteAddress = call.getAttributes().get(Grpc.TRANSPORT_ATTR_REMOTE_ADDR);
        var ipAddress = remoteAddress == null ? "" : remoteAddress.toString();
        var parentCtx = W3CTraceContextPropagator.getInstance().extract(Context.root(), headers, new TextMapGetter<>() {
            @Override
            public Iterable<String> keys(Metadata carrier) {
                return carrier.keys();
            }

            @Override
            public String get(Metadata carrier, String key) {
                return carrier.get(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER));
            }
        });
        var span = this.context.tracer()
            .spanBuilder(serviceName + "/" + methodName)
            .setSpanKind(SpanKind.SERVER)
            .setParent(parentCtx)
            .setAttribute(ServerAttributes.SERVER_PORT, this.context.port())
            .setAttribute("server.name", this.context.name())
            .setAttribute(RpcIncubatingAttributes.RPC_SYSTEM, "grpc")
            .setAttribute(RpcIncubatingAttributes.RPC_SERVICE, serviceName)
            .setAttribute(RpcIncubatingAttributes.RPC_METHOD, methodName)
            .setAttribute(NetworkAttributes.NETWORK_PEER_ADDRESS, ipAddress);

        for (var entry : this.context.config().tracing().attributes().entrySet()) {
            span.setAttribute(entry.getKey(), entry.getValue());
        }

        return span.startSpan();
    }

    protected String service(ServerCall<?, ?> call) {
        var fullMethodName = call.getMethodDescriptor().getFullMethodName();
        int slashIndex = fullMethodName.lastIndexOf('/');
        if (slashIndex == -1) {
            return "unknownService";
        }
        return fullMethodName.substring(0, slashIndex);
    }

    protected String method(ServerCall<?, ?> call) {
        String fullMethodName = call.getMethodDescriptor().getFullMethodName();
        int slashIndex = fullMethodName.lastIndexOf('/');
        if (slashIndex == -1) {
            return "unknownMethod";
        }
        return fullMethodName.substring(slashIndex + 1);
    }
}
