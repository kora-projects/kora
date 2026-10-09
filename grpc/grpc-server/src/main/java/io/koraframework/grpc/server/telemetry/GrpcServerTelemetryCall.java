package io.koraframework.grpc.server.telemetry;

import io.grpc.*;
import io.opentelemetry.context.Context;
import io.koraframework.common.telemetry.Observation;
import io.koraframework.common.telemetry.OpentelemetryContext;

public class GrpcServerTelemetryCall<ReqT, RespT> extends ForwardingServerCall<ReqT, RespT> {
    // captured while grpc-java has the call context attached, a handler may close from a thread without it
    private final io.grpc.Context grpcContext = io.grpc.Context.current();
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
                if (this.call.isCancelled()) {
                    // the client already got CANCELLED or DEADLINE_EXCEEDED, the handler's late close is a no-op
                    this.observation.observeClose(this.cancelledStatus(), new Metadata());
                } else {
                    this.observation.observeClose(status, trailers);
                }
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

    private Status cancelledStatus() {
        var status = Contexts.statusFromCancelled(this.grpcContext);
        // the cause is grpc's own cancel/timeout exception, not a server error: record only code and description
        return status != null ? Status.fromCode(status.getCode()).withDescription(status.getDescription()) : Status.CANCELLED;
    }
}
