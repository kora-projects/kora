package ru.tinkoff.grpc.client.telemetry;

import io.grpc.CallOptions;
import io.grpc.MethodDescriptor;
import io.grpc.ServiceDescriptor;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.incubating.RpcIncubatingAttributes;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.net.URI;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultGrpcClientTelemetry implements GrpcClientTelemetry {
    private final TelemetryConfig config;
    private final Tracer tracer;
    private final MeterRegistry meterRegistry;
    private final ServiceDescriptor service;
    private final URI uri;
    private final ConcurrentHashMap<String, ConcurrentHashMap<Tags, Timer>> durationCache = new ConcurrentHashMap<>();

    public DefaultGrpcClientTelemetry(TelemetryConfig config, Tracer tracer, MeterRegistry meterRegistry, ServiceDescriptor service, URI uri) {
        this.config = config;
        this.tracer = tracer;
        this.meterRegistry = meterRegistry;
        this.service = service;
        this.uri = uri;
    }

    @Override
    public <ReqT, RespT> GrpcClientObservation observe(MethodDescriptor<ReqT, RespT> method, CallOptions callOptions) {
        var span = this.createSpan(method);
        var duration = this.clientDurationMetric(method);

        return new DefaultGrpcClientObservation(span, duration);
    }

    protected Meter.MeterProvider<Timer> clientDurationMetric(MethodDescriptor<?, ?> method) {
        var methodName = Objects.requireNonNullElse(method.getBareMethodName(), "");
        var methodCache = this.durationCache.computeIfAbsent(methodName, k -> new ConcurrentHashMap<>());

        return tags -> methodCache.computeIfAbsent(Tags.of(tags), t -> {
            var serverAddress = uri.getHost();
            var serverPort = uri.getPort();
            if (serverPort == -1) {
                serverPort = 80;
            }
            var b = Timer.builder("rpc.client.duration")
                .tag(RpcIncubatingAttributes.RPC_METHOD.getKey(), methodName)
                .tag(RpcIncubatingAttributes.RPC_SERVICE.getKey(), Objects.requireNonNullElse(this.service.getName(), "GrpcService"))
                .tag(RpcIncubatingAttributes.RPC_SYSTEM.getKey(), RpcIncubatingAttributes.RpcSystemIncubatingValues.GRPC)
                .tag(ServerAttributes.SERVER_ADDRESS.getKey(), serverAddress)
                .tag(ServerAttributes.SERVER_PORT.getKey(), String.valueOf(serverPort))
                .serviceLevelObjectives(this.config.metrics().slo());
            for (var entry : this.config.metrics().tags().entrySet()) {
                b.tag(entry.getKey(), entry.getValue());
            }
            return b.tags(t).register(this.meterRegistry);
        });
    }

    protected Span createSpan(MethodDescriptor<?, ?> method) {
        var span = this.tracer.spanBuilder(method.getFullMethodName())
            .setAttribute("rpc.method", Objects.requireNonNullElse(method.getBareMethodName(), "unknownMethod"))
            .setAttribute("rpc.service", this.service.getName())
            .setAttribute("rpc.system", "grpc")
            .setAttribute("server.address", this.uri.getHost())
            .setAttribute("server.port", this.uri.getPort())
            .setSpanKind(SpanKind.CLIENT);
        for (var entry : this.config.tracing().attributes().entrySet()) {
            span.setAttribute(entry.getKey(), entry.getValue());
        }

        return span.startSpan();
    }
}
