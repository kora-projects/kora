package ru.tinkoff.grpc.client.telemetry;

import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.Status;
import io.opentelemetry.context.Context;
import ru.tinkoff.kora.common.telemetry.Observation;
import ru.tinkoff.kora.common.telemetry.OpentelemetryContext;

public class GrpcClientTelemetryResponseListener<RespT> extends ClientCall.Listener<RespT> {
    private final Context context;
    private final GrpcClientObservation observation;
    private final ClientCall.Listener<RespT> delegate;

    public GrpcClientTelemetryResponseListener(Context context, GrpcClientObservation observation, ClientCall.Listener<RespT> delegate) {
        this.context = context;
        this.observation = observation;
        this.delegate = delegate;
    }

    @Override
    public void onClose(Status status, Metadata trailers) {
        ScopedValue.where(Observation.VALUE, observation)
            .where(OpentelemetryContext.VALUE, context)
            .run(() -> {
                this.observation.observeClose(status, trailers);
                try {
                    this.delegate.onClose(status, trailers);
                } catch (Throwable e) {
                    this.observation.observeError(e);
                    throw e;
                } finally {
                    this.observation.end();
                }
            });
    }

    @Override
    public void onMessage(RespT message) {
        ScopedValue.where(Observation.VALUE, observation)
            .where(OpentelemetryContext.VALUE, context)
            .run(() -> {
                this.observation.observeReceive(message);
                try {
                    this.delegate.onMessage(message);
                } catch (Throwable e) {
                    this.observation.observeError(e);
                    this.observation.end();
                    throw e;
                }
            });
    }

}
