package ru.tinkoff.grpc.client.telemetry;

import io.grpc.ClientCall;
import io.grpc.ForwardingClientCall;
import io.grpc.Metadata;
import io.opentelemetry.context.Context;
import ru.tinkoff.kora.common.telemetry.Observation;
import ru.tinkoff.kora.common.telemetry.OpentelemetryContext;

public final class GrpcClientTelemetryCall<ReqT, RespT> extends ForwardingClientCall<ReqT, RespT> {
    private final GrpcClientObservation observation;
    private final Context context;
    private final ClientCall<ReqT, RespT> delegate;

    public GrpcClientTelemetryCall(Context context, GrpcClientObservation observation, ClientCall<ReqT, RespT> delegate) {
        this.observation = observation;
        this.context = context;
        this.delegate = delegate;
    }

    @Override
    protected ClientCall<ReqT, RespT> delegate() {
        return this.delegate;
    }

    @Override
    public void start(Listener<RespT> responseListener, Metadata headers) {
        ScopedValue.where(Observation.VALUE, observation)
            .where(OpentelemetryContext.VALUE, context)
            .run(() -> {
                this.observation.observeStart(headers);
                var listener = new GrpcClientTelemetryResponseListener<>(this.context, this.observation, responseListener);
                try {
                    this.delegate.start(listener, headers);
                } catch (Throwable e) {
                    this.observation.observeError(e);
                    this.observation.end();
                    throw e;
                }
            });
    }

    @Override
    public void sendMessage(ReqT message) {
        ScopedValue.where(Observation.VALUE, observation)
            .where(OpentelemetryContext.VALUE, context)
            .run(() -> {
                this.observation.observeSend(message);
                try {
                    super.sendMessage(message);
                } catch (Throwable e) {
                    this.observation.observeError(e);
                    this.observation.end();
                    throw e;
                }
            });
    }
}
