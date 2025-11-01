package ru.tinkoff.grpc.client.telemetry;

import io.grpc.*;
import io.opentelemetry.context.Context;

public final class GrpcClientTelemetryInterceptor implements ClientInterceptor {
    private final GrpcClientTelemetry telemetry;

    public GrpcClientTelemetryInterceptor(GrpcClientTelemetry telemetry) {
        this.telemetry = telemetry;
    }

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
        var observation = this.telemetry.observe(method, callOptions);
        if (observation == NoopGrpcClientObservation.INSTANCE) {
            return next.newCall(method, callOptions);
        }
        final ClientCall<ReqT, RespT> call;
        try {
            call = next.newCall(method, callOptions);
        } catch (Throwable e) {
            observation.observeError(e);
            observation.end();
            throw e;
        }
        var context = Context.current();
        return new GrpcClientTelemetryCall<>(context, observation, call);
    }
}
