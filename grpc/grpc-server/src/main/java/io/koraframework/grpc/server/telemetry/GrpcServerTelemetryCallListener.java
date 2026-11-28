package io.koraframework.grpc.server.telemetry;

import io.grpc.ForwardingServerCallListener;
import io.grpc.ServerCall;
import io.opentelemetry.context.Context;
import io.koraframework.common.telemetry.Observation;
import io.koraframework.common.telemetry.OpentelemetryContextStorage;
import io.koraframework.common.context.KoraContext;
import java.util.function.Supplier;

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
        KoraContext current = KoraContext.current();
        KoraContext baseContext = current != null ? current : KoraContext.empty();

        KoraContext updatedContext = Observation.attach(baseContext, observation)
            .with(OpentelemetryContextStorage.OPENTELEMETRY_KEY, context);

        KoraContext.runWith(updatedContext, new OnCancelRunner<>(this));
    }

    @Override
    public void onComplete() {
        KoraContext current = KoraContext.current();
        KoraContext baseContext = current != null ? current : KoraContext.empty();

        KoraContext updatedContext = Observation.attach(baseContext, observation)
            .with(OpentelemetryContextStorage.OPENTELEMETRY_KEY, context);

        KoraContext.runWith(updatedContext, new OnCompleteRunner<>(this));
    }

    @Override
    public void onHalfClose() {
        KoraContext current = KoraContext.current();
        KoraContext baseContext = current != null ? current : KoraContext.empty();

        KoraContext updatedContext = Observation.attach(baseContext, observation)
            .with(OpentelemetryContextStorage.OPENTELEMETRY_KEY, context);

        KoraContext.runWith(updatedContext, new OnHalfCloseRunner<>(this));
    }

    @Override
    public void onMessage(ReqT message) {
        KoraContext current = KoraContext.current();
        KoraContext baseContext = current != null ? current : KoraContext.empty();

        KoraContext updatedContext = Observation.attach(baseContext, observation)
            .with(OpentelemetryContextStorage.OPENTELEMETRY_KEY, context);

        KoraContext.runWith(updatedContext, new OnMessageRunner<>(this, message));
    }

    @Override
    public void onReady() {
        KoraContext current = KoraContext.current();
        KoraContext baseContext = current != null ? current : KoraContext.empty();

        KoraContext updatedContext = Observation.attach(baseContext, observation)
            .with(OpentelemetryContextStorage.OPENTELEMETRY_KEY, context);

        KoraContext.runWith(updatedContext, new OnReadyRunner<>(this));
    }

    private static final class OnCancelRunner<ReqT> implements Supplier<Void> {
        private final GrpcServerTelemetryCallListener<ReqT> serverListener;

        public OnCancelRunner(GrpcServerTelemetryCallListener<ReqT> serverListener) {
            this.serverListener = serverListener;
        }

        @Override
        public Void get() {
            serverListener.observation.observeCancel();
            serverListener.listener.onCancel();
            return null;
        }
    }

    private static final class OnCompleteRunner<ReqT> implements Supplier<Void> {
        private final GrpcServerTelemetryCallListener<ReqT> serverListener;

        public OnCompleteRunner(GrpcServerTelemetryCallListener<ReqT> serverListener) {
            this.serverListener = serverListener;
        }

        @Override
        public Void get() {
            serverListener.observation.observeComplete();
            serverListener.listener.onComplete();
            return null;
        }
    }

    private static final class OnHalfCloseRunner<ReqT> implements Supplier<Void> {
        private final GrpcServerTelemetryCallListener<ReqT> serverListener;

        public OnHalfCloseRunner(GrpcServerTelemetryCallListener<ReqT> serverListener) {
            this.serverListener = serverListener;
        }

        @Override
        public Void get() {
            serverListener.observation.observeHalfClosed();
            serverListener.listener.onHalfClose();
            return null;
        }
    }

    private static final class OnMessageRunner<ReqT> implements Supplier<Void> {
        private final GrpcServerTelemetryCallListener<ReqT> serverListener;
        private final ReqT message;

        public OnMessageRunner(GrpcServerTelemetryCallListener<ReqT> serverListener, ReqT message) {
            this.serverListener = serverListener;
            this.message = message;
        }

        @Override
        public Void get() {
            serverListener.observation.observeReceiveMessage(message);
            serverListener.listener.onMessage(message);
            return null;
        }
    }

    private static final class OnReadyRunner<ReqT> implements Supplier<Void> {
        private final GrpcServerTelemetryCallListener<ReqT> serverListener;

        public OnReadyRunner(GrpcServerTelemetryCallListener<ReqT> serverListener) {
            this.serverListener = serverListener;
        }

        @Override
        public Void get() {
            serverListener.observation.observeReady();
            serverListener.listener.onReady();
            return null;
        }
    }
}
