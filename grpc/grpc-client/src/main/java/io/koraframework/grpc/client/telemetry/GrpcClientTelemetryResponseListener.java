package io.koraframework.grpc.client.telemetry;

import io.grpc.*;
import io.koraframework.common.context.*;
import io.koraframework.common.telemetry.*;
import io.opentelemetry.context.Context;

import java.util.function.*;

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
        KoraContext current = KoraContext.current();
        KoraContext baseContext = current != null ? current : KoraContext.empty();

        KoraContext updatedContext = Observation.attach(baseContext, observation)
            .with(OpentelemetryContextStorage.OPENTELEMETRY_KEY, context);

        KoraContext.runWith(updatedContext, new OnCloseRunner<>(this, status, trailers));
    }

    @Override
    public void onMessage(RespT message) {
        KoraContext current = KoraContext.current();
        KoraContext baseContext = current != null ? current : KoraContext.empty();

        KoraContext updatedContext = Observation.attach(baseContext, observation)
            .with(OpentelemetryContextStorage.OPENTELEMETRY_KEY, context);

        KoraContext.runWith(updatedContext, new OnMessageRunner<>(this, message));
    }

    private static final class OnCloseRunner<RespT> implements Supplier<Void> {
        private final GrpcClientTelemetryResponseListener<RespT> listener;
        private final Status status;
        private final Metadata trailers;

        public OnCloseRunner(GrpcClientTelemetryResponseListener<RespT> listener, Status status, Metadata trailers) {
            this.listener = listener;
            this.status = status;
            this.trailers = trailers;
        }

        @Override
        public Void get() {
            listener.observation.observeClose(status, trailers);
            try {
                listener.delegate.onClose(status, trailers);
            } catch (Throwable e) {
                listener.observation.observeError(e);
                throw e;
            } finally {
                listener.observation.end();
            }
            return null;
        }
    }

    private static final class OnMessageRunner<RespT> implements Supplier<Void> {
        private final GrpcClientTelemetryResponseListener<RespT> listener;
        private final RespT message;

        public OnMessageRunner(GrpcClientTelemetryResponseListener<RespT> listener, RespT message) {
            this.listener = listener;
            this.message = message;
        }

        @Override
        public Void get() {
            listener.observation.observeReceive(message);
            try {
                listener.delegate.onMessage(message);
            } catch (Throwable e) {
                listener.observation.observeError(e);
                listener.observation.end();
                throw e;
            }
            return null;
        }
    }
}
