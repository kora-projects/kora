package io.koraframework.grpc.server.telemetry;

import io.grpc.ForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import io.opentelemetry.context.Context;
import io.koraframework.common.telemetry.Observation;
import io.koraframework.common.telemetry.OpentelemetryContext;

import java.util.Objects;

public class GrpcServerTelemetryCallListener<ReqT> extends ForwardingServerCallListener<ReqT> {
    private final Context context;
    private final GrpcServerObservation observation;
    private final GrpcServerTelemetryCall<ReqT, ?> call;
    private final ServerCall.Listener<ReqT> listener;

    public GrpcServerTelemetryCallListener(Context context, GrpcServerObservation observation, GrpcServerTelemetryCall<ReqT, ?> call, ServerCall.Listener<ReqT> listener) {
        this.context = context;
        this.observation = observation;
        this.call = call;
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
                if (!this.call.isEnded()) {
                    this.observation.observeClose(Status.CANCELLED, new Metadata());
                }
                try {
                    this.listener.onCancel();
                } finally {
                    this.call.end();
                }
            });
    }

    @Override
    public void onComplete() {
        ScopedValue.where(Observation.VALUE, observation)
            .where(OpentelemetryContext.VALUE, context)
            .run(() -> {
                this.observation.observeComplete();
                try {
                    this.listener.onComplete();
                } finally {
                    this.call.end();
                }
            });
    }

    @Override
    public void onHalfClose() {
        ScopedValue.where(Observation.VALUE, observation)
            .where(OpentelemetryContext.VALUE, context)
            .run(() -> {
                this.observation.observeHalfClosed();
                try {
                    this.listener.onHalfClose();
                } catch (Throwable e) {
                    this.closeOnError(e);
                    throw e;
                }
            });
    }

    @Override
    public void onMessage(ReqT message) {
        ScopedValue.where(Observation.VALUE, observation)
            .where(OpentelemetryContext.VALUE, context)
            .run(() -> {
                this.observation.observeReceiveMessage(message);
                try {
                    this.listener.onMessage(message);
                } catch (Throwable e) {
                    this.closeOnError(e);
                    throw e;
                }
            });
    }

    @Override
    public void onReady() {
        ScopedValue.where(Observation.VALUE, observation)
            .where(OpentelemetryContext.VALUE, context)
            .run(() -> {
                this.observation.observeReady();
                try {
                    this.listener.onReady();
                } catch (Throwable e) {
                    this.closeOnError(e);
                    throw e;
                }
            });
    }

    /**
     * grpc-java closes the stream itself when a listener callback throws, bypassing the wrapped {@link ServerCall},
     * so the observation would never end and the thrown {@link Status} would be replaced with UNKNOWN.
     * The call is closed here first; the caller then rethrows, so grpc-java still logs the exception
     * and its own close of the already closed stream is a no-op.
     */
    private void closeOnError(Throwable e) {
        if (this.call.isEnded()) {
            return;
        }
        this.observation.observeError(e);
        final Status status = e instanceof StatusRuntimeException || e instanceof StatusException
            ? Status.fromThrowable(e)
            // no cause: observeClose would record the exception a second time
            : Status.UNKNOWN.withDescription("Application error processing RPC");
        try {
            this.call.close(status, Objects.requireNonNullElseGet(Status.trailersFromThrowable(e), Metadata::new));
        } catch (IllegalStateException ignored) {
            // call was already closed by the handler
        }
    }
}
