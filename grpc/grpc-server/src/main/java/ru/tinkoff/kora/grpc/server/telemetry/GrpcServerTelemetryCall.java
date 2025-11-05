package ru.tinkoff.kora.grpc.server.telemetry;

import io.grpc.*;
import io.opentelemetry.context.Context;
import ru.tinkoff.kora.common.telemetry.Observation;
import ru.tinkoff.kora.common.telemetry.OpentelemetryContext;

public class GrpcServerTelemetryCall<ReqT, RespT> extends ForwardingServerCall<ReqT, RespT> {
    private final Context context;
    private final GrpcServerObservation observation;
    private final ServerCall<ReqT, RespT> call;

    public GrpcServerTelemetryCall(Context context, GrpcServerObservation observation, ServerCall<ReqT, RespT> call) {
        this.context = context;
        this.observation = observation;
        this.call = call;
    }

    @Override
    protected ServerCall<ReqT, RespT> delegate() {
        return this.call;
    }

    @Override
    public void request(int numMessages) {
        ScopedValue.where(Observation.VALUE, observation)
            .where(OpentelemetryContext.VALUE, context)
            .run(() -> {
                this.observation.observeRequest(numMessages);
                this.call.request(numMessages);
            });
    }

    @Override
    public void sendHeaders(Metadata headers) {
        ScopedValue.where(Observation.VALUE, observation)
            .where(OpentelemetryContext.VALUE, context)
            .run(() -> {
                this.observation.observeHeaders(headers);
                this.call.sendHeaders(headers);
            });
    }

    @Override
    public void sendMessage(RespT message) {
        ScopedValue.where(Observation.VALUE, observation)
            .where(OpentelemetryContext.VALUE, context)
            .run(() -> {
                this.observation.observeSendMessage(message);
                this.call.sendMessage(message);
            });
    }

    @Override
    public void close(Status status, Metadata trailers) {
        ScopedValue.where(Observation.VALUE, observation)
            .where(OpentelemetryContext.VALUE, context)
            .run(() -> {
                this.observation.observeClose(status, trailers);
                try {
                    this.call.close(status, trailers);
                } finally {
                    this.observation.end();
                }
            });
    }

    @Override
    public boolean isCancelled() {
        return this.call.isCancelled();
    }

    @Override
    public MethodDescriptor<ReqT, RespT> getMethodDescriptor() {
        return this.call.getMethodDescriptor();
    }
}
