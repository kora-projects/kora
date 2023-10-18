package ru.tinkoff.kora.opentelemetry.module.grpc.client;

import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServiceDescriptor;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import ru.tinkoff.grpc.client.telemetry.GrpcClientTracer;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.opentelemetry.common.OpentelemetryContext;

import java.net.URI;
import java.util.Objects;

public final class OpentelemetryGrpcClientTracer implements GrpcClientTracer {

    private final Tracer tracer;
    private final ServiceDescriptor descriptor;
    private final URI uri;

    public OpentelemetryGrpcClientTracer(Tracer tracer, ServiceDescriptor descriptor, URI uri) {
        this.tracer = tracer;
        this.descriptor = descriptor;
        this.uri = uri;
    }

    @Override
    public <RespT, ReqT> GrpcClientSpan callSpan(Context ctx, MethodDescriptor<ReqT, RespT> method, URI uri, ClientCall<ReqT, RespT> call, Metadata headers) {
        var otctx = OpentelemetryContext.get(ctx);
        var span = this.tracer.spanBuilder(method.getFullMethodName())
            .setAttribute("rpc.method", Objects.requireNonNullElse(method.getBareMethodName(), "unknownMethod"))
            .setAttribute("rpc.service", this.descriptor.getName())
            .setAttribute("rpc.system", "grpc")
            .setAttribute("server.address", this.uri.getHost())
            .setAttribute("server.port", this.uri.getPort())
            .setSpanKind(SpanKind.CLIENT)
            .setParent(otctx.getContext())
            .startSpan();

        var newCtx = otctx.add(span);
        OpentelemetryContext.set(ctx, newCtx);
        W3CTraceContextPropagator.getInstance().inject(
            newCtx.getContext(),
            headers,
            (carrier, key, value) -> carrier.put(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER), value)
        );

        return new OpentelemetryGrpcClientSpan(this.tracer, span, descriptor, uri);
    }


}
