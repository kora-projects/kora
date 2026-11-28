package io.koraframework.grpc.client.telemetry;

import io.grpc.*;
import io.koraframework.common.context.*;
import io.koraframework.common.telemetry.*;
import io.opentelemetry.context.Context;

import java.util.function.*;

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
        KoraContext current = KoraContext.current();
        KoraContext baseContext = current != null ? current : KoraContext.empty();

        KoraContext updatedContext = Observation.attach(baseContext, observation)
            .with(OpentelemetryContextStorage.OPENTELEMETRY_KEY, context);

        KoraContext.runWith(updatedContext, new StartRunner<>(this, responseListener, headers));
    }

    @Override
    public void sendMessage(ReqT message) {
        KoraContext current = KoraContext.current();
        KoraContext baseContext = current != null ? current : KoraContext.empty();

        KoraContext updatedContext = Observation.attach(baseContext, observation)
            .with(OpentelemetryContextStorage.OPENTELEMETRY_KEY, context);

        KoraContext.runWith(updatedContext, new SendMessageRunner<>(this, message));
    }

    private static final class StartRunner<ReqT, RespT> implements Supplier<Void> {
        private final GrpcClientTelemetryCall<ReqT, RespT> call;
        private final Listener<RespT> responseListener;
        private final Metadata headers;

        public StartRunner(GrpcClientTelemetryCall<ReqT, RespT> call, Listener<RespT> responseListener, Metadata headers) {
            this.call = call;
            this.responseListener = responseListener;
            this.headers = headers;
        }

        @Override
        public Void get() {
            call.observation.observeStart(headers);
            var listener = new GrpcClientTelemetryResponseListener<>(call.context, call.observation, responseListener);
            try {
                call.delegate.start(listener, headers);
            } catch (Throwable e) {
                call.observation.observeError(e);
                call.observation.end();
                throw e;
            }
            return null;
        }
    }

    private static final class SendMessageRunner<ReqT, RespT> implements Supplier<Void> {
        private final GrpcClientTelemetryCall<ReqT, RespT> call;
        private final ReqT message;

        public SendMessageRunner(GrpcClientTelemetryCall<ReqT, RespT> call, ReqT message) {
            this.call = call;
            this.message = message;
        }

        @Override
        public Void get() {
            call.observation.observeSend(message);
            try {
                call.delegate.sendMessage(message);
            } catch (Throwable e) {
                call.observation.observeError(e);
                call.observation.end();
                throw e;
            }
            return null;
        }
    }
}
