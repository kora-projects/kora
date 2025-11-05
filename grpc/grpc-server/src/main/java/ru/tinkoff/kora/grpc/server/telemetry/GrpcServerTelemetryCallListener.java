package ru.tinkoff.kora.grpc.server.telemetry;

import io.grpc.ForwardingServerCallListener;
import io.grpc.ServerCall;
import io.opentelemetry.context.Context;
import ru.tinkoff.kora.common.telemetry.Observation;
import ru.tinkoff.kora.common.telemetry.OpentelemetryContext;

public class GrpcServerTelemetryCallListener<ReqT> extends ForwardingServerCallListener<ReqT> {
    private final Context context;
    private final GrpcServerObservation observation;
    private final ServerCall.Listener<ReqT> listener;

    public GrpcServerTelemetryCallListener(Context context, GrpcServerObservation observation, ServerCall.Listener<ReqT> listener) {
        this.context = context;
        this.observation = observation;
        this.listener = listener;
    }

    @Override
    protected ServerCall.Listener<ReqT> delegate() {
        return this.listener;
    }

    @Override
    public void onCancel() {
        ScopedValue.where(Observation.VALUE, observation)
            .where(OpentelemetryContext.VALUE, context)
            .run(() -> {
                this.observation.observeCancel();
                this.listener.onCancel();
            });
    }

    @Override
    public void onComplete() {
        ScopedValue.where(Observation.VALUE, observation)
            .where(OpentelemetryContext.VALUE, context)
            .run(() -> {
                this.observation.observeComplete();
                this.listener.onComplete();
            });
    }

    @Override
    public void onHalfClose() {
        ScopedValue.where(Observation.VALUE, observation)
            .where(OpentelemetryContext.VALUE, context)
            .run(() -> {
                this.observation.observeHalfClosed();
                this.listener.onHalfClose();
            });
    }

    @Override
    public void onMessage(ReqT message) {
        ScopedValue.where(Observation.VALUE, observation)
            .where(OpentelemetryContext.VALUE, context)
            .run(() -> {
                this.observation.observeReceiveMessage(message);
                this.listener.onMessage(message);
            });
    }

    @Override
    public void onReady() {
        ScopedValue.where(Observation.VALUE, observation)
            .where(OpentelemetryContext.VALUE, context)
            .run(() -> {
                this.observation.observeReady();
                this.listener.onReady();
            });
    }
}
