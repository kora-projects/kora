package ru.tinkoff.kora.grpc.server.telemetry;

import io.grpc.Grpc;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.noop.NoopTimer;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.semconv.NetworkAttributes;
import io.opentelemetry.semconv.incubating.RpcIncubatingAttributes;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultGrpcServerTelemetry implements GrpcServerTelemetry {
    private static final Meter.Id NOOP_TIMER_ID = new Meter.Id("", Tags.empty(), null, null, Meter.Type.TIMER);

    private final Tracer tracer;
    private final MeterRegistry meterRegistry;
    private final GrpcServerTelemetryConfig config;
    private final ConcurrentHashMap<DurationKey, ConcurrentHashMap<Tags, Timer>> durationCache = new ConcurrentHashMap<>();

    public DefaultGrpcServerTelemetry(GrpcServerTelemetryConfig config, Tracer tracer, MeterRegistry meterRegistry) {
        this.tracer = tracer;
        this.meterRegistry = meterRegistry;
        this.config = config;
    }

    @Override
    public GrpcServerObservation observe(ServerCall<?, ?> call, Metadata headers) {
        var service = this.service(call);
        var method = this.method(call);
        var span = this.createSpan(call, headers, service, method);
        var duration = this.duration(call, service, method);
        return new DefaultGrpcServerObservation(this.config, service, method, headers, span, duration);
    }

    record DurationKey(String service, String method) {}

    protected Meter.MeterProvider<Timer> duration(ServerCall<?, ?> call, String service, String method) {
        if (!this.config.metrics().enabled()) {
            return _ -> new NoopTimer(NOOP_TIMER_ID);
        }
        var cache = this.durationCache.computeIfAbsent(new DurationKey(service, method), _ -> new ConcurrentHashMap<>());

        return additionalTags -> cache.computeIfAbsent(Tags.of(additionalTags), t -> {
            var tags = new ArrayList<Tag>();
            tags.add(Tag.of(RpcIncubatingAttributes.RPC_SYSTEM.getKey(), RpcIncubatingAttributes.RpcSystemIncubatingValues.GRPC));
            tags.add(Tag.of(RpcIncubatingAttributes.RPC_SERVICE.getKey(), service));
            tags.add(Tag.of(RpcIncubatingAttributes.RPC_METHOD.getKey(), method));
            for (var tag : t) {
                tags.add(tag);
            }
            for (var entry : this.config.metrics().tags().entrySet()) {
                tags.add(Tag.of(entry.getKey(), entry.getValue()));
            }
            return Timer.builder("rpc.server.duration")
                .serviceLevelObjectives(config.metrics().slo())
                .tags(tags)
                .register(this.meterRegistry);
        });
    }

    protected Span createSpan(ServerCall<?, ?> call, Metadata headers, String serviceName, String methodName) {
        if (!this.config.tracing().enabled()) {
            return Span.getInvalid();
        }
        var ipAddress = call.getAttributes().get(Grpc.TRANSPORT_ATTR_REMOTE_ADDR).toString();
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
        var span = this.tracer
            .spanBuilder(serviceName + "/" + methodName)
            .setSpanKind(SpanKind.SERVER)
            .setParent(parentCtx)
            .setAttribute(RpcIncubatingAttributes.RPC_SYSTEM, "grpc")
            .setAttribute(RpcIncubatingAttributes.RPC_SERVICE, serviceName)
            .setAttribute(RpcIncubatingAttributes.RPC_METHOD, methodName)
            .setAttribute(NetworkAttributes.NETWORK_PEER_ADDRESS, ipAddress);

        for (var entry : this.config.tracing().attributes().entrySet()) {
            span.setAttribute(entry.getKey(), entry.getValue());
        }

        return span.startSpan();
    }

    private String service(ServerCall<?, ?> call) {
        var fullMethodName = call.getMethodDescriptor().getFullMethodName();
        int slashIndex = fullMethodName.lastIndexOf('/');
        if (slashIndex == -1) {
            return "unknownService";
        }
        return fullMethodName.substring(0, slashIndex);
    }

    private String method(ServerCall<?, ?> call) {
        String fullMethodName = call.getMethodDescriptor().getFullMethodName();
        int slashIndex = fullMethodName.lastIndexOf('/');
        if (slashIndex == -1) {
            return "unknownMethod";
        }
        return fullMethodName.substring(slashIndex + 1);
    }
}
