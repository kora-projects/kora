package ru.tinkoff.kora.grpc.server.interceptors;

import io.grpc.*;
import io.opentelemetry.context.Context;
import ru.tinkoff.kora.application.graph.ValueOf;
import ru.tinkoff.kora.common.telemetry.Observation;
import ru.tinkoff.kora.common.telemetry.OpentelemetryContext;
import ru.tinkoff.kora.grpc.server.telemetry.GrpcServerTelemetry;
import ru.tinkoff.kora.grpc.server.telemetry.GrpcServerTelemetryCall;
import ru.tinkoff.kora.grpc.server.telemetry.GrpcServerTelemetryCallListener;

public class TelemetryInterceptor implements ServerInterceptor {
    private final ValueOf<GrpcServerTelemetry> telemetry;

    public TelemetryInterceptor(ValueOf<GrpcServerTelemetry> telemetry) {
        this.telemetry = telemetry;
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        var observation = this.telemetry.get().observe(call, headers);
        var context = Context.root()
            .with(observation.span());
        return ScopedValue.where(Observation.VALUE, observation)
            .where(OpentelemetryContext.VALUE, context)
            .call(() -> {
                try {
                    var telemetryCall = new GrpcServerTelemetryCall<>(context, observation, call);
                    observation.observeStart();
                    var listener = next.startCall(telemetryCall, headers);
                    return new GrpcServerTelemetryCallListener<>(context, observation, listener);
                } catch (StatusRuntimeException e) {
                    observation.observeClose(e.getStatus(), e.getTrailers());
                    observation.observeError(e);
                    observation.end();
                    throw e;
                } catch (Throwable e) {
                    observation.observeError(e);
                    observation.end();
                    throw e;
                }
            });
    }
}
