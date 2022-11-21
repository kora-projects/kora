package ru.tinkoff.kora.opentelemetry.module.grpc.server;

import io.grpc.Grpc;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.grpc.telemetry.GrpcServerTracer;
import ru.tinkoff.kora.opentelemetry.common.OpentelemetryContext;

import javax.annotation.Nullable;

import static io.opentelemetry.context.Context.root;

public final class OpentelemetryGrpcServerTracer implements GrpcServerTracer {
    private final Tracer tracer;

    public OpentelemetryGrpcServerTracer(Tracer tracer) {
        this.tracer = tracer;
    }

    private enum GrpcHeaderMapGetter implements TextMapGetter<Metadata> {
        INSTANCE;

        @Override
        public Iterable<String> keys(Metadata carrier) {
            return carrier.keys();
        }

        @Nullable
        @Override
        public String get(@Nullable Metadata carrier, String key) {
            return carrier.get(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER));
        }
    }

    @Override
    public GrpcServerSpan createSpan(ServerCall<?, ?> call, Metadata headers, String serviceName, String methodName) {
        var ipAddress = call.getAttributes().get(Grpc.TRANSPORT_ATTR_REMOTE_ADDR).toString();
        var context = Context.current();
        var parentCtx = W3CTraceContextPropagator.getInstance().extract(root(), headers, OpentelemetryGrpcServerTracer.GrpcHeaderMapGetter.INSTANCE);
        var span = this.tracer
            .spanBuilder(serviceName + "/" + methodName)
            .setSpanKind(SpanKind.SERVER)
            .setParent(parentCtx)
            .setAttribute(SemanticAttributes.RPC_SYSTEM, "grpc")
            .setAttribute(SemanticAttributes.RPC_SERVICE, serviceName)
            .setAttribute(SemanticAttributes.RPC_METHOD, methodName)
            .setAttribute(SemanticAttributes.NET_SOCK_PEER_ADDR, ipAddress)
            .startSpan();

        OpentelemetryContext.set(context, OpentelemetryContext.get(context).add(span));


        return new OpentelemetryGrpcServerSpan(span);
    }
}
