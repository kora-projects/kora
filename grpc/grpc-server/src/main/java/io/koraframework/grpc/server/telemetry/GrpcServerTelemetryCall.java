package io.koraframework.grpc.server.telemetry;

import io.grpc.*;
import io.opentelemetry.context.Context;
import io.koraframework.common.telemetry.Observation;
import io.koraframework.common.telemetry.OpentelemetryContextStorage;
import io.koraframework.common.context.KoraContext;
import java.util.function.Supplier;

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
        KoraContext current = KoraContext.current();
        KoraContext baseContext = current != null ? current : KoraContext.empty();

        KoraContext updatedContext = Observation.attach(baseContext, observation)
            .with(OpentelemetryContextStorage.OPENTELEMETRY_KEY, context);

        KoraContext.runWith(updatedContext, new RequestRunner<>(this, numMessages));
    }

    @Override
    public void sendHeaders(Metadata headers) {
        KoraContext current = KoraContext.current();
        KoraContext baseContext = current != null ? current : KoraContext.empty();

        KoraContext updatedContext = Observation.attach(baseContext, observation)
            .with(OpentelemetryContextStorage.OPENTELEMETRY_KEY, context);

        KoraContext.runWith(updatedContext, new SendHeadersRunner<>(this, headers));
    }

    @Override
    public void sendMessage(RespT message) {
        KoraContext current = KoraContext.current();
        KoraContext baseContext = current != null ? current : KoraContext.empty();

        KoraContext updatedContext = Observation.attach(baseContext, observation)
            .with(OpentelemetryContextStorage.OPENTELEMETRY_KEY, context);

        KoraContext.runWith(updatedContext, new SendMessageRunner<>(this, message));
    }

    @Override
    public void close(Status status, Metadata trailers) {
        KoraContext current = KoraContext.current();
        KoraContext baseContext = current != null ? current : KoraContext.empty();

        KoraContext updatedContext = Observation.attach(baseContext, observation)
            .with(OpentelemetryContextStorage.OPENTELEMETRY_KEY, context);

        KoraContext.runWith(updatedContext, new CloseRunner<>(this, status, trailers));
    }

    @Override
    public boolean isCancelled() {
        return this.call.isCancelled();
    }

    @Override
    public MethodDescriptor<ReqT, RespT> getMethodDescriptor() {
        return this.call.getMethodDescriptor();
    }

    private static final class RequestRunner<ReqT, RespT> implements Supplier<Void> {
        private final GrpcServerTelemetryCall<ReqT, RespT> serverCall;
        private final int numMessages;

        public RequestRunner(GrpcServerTelemetryCall<ReqT, RespT> serverCall, int numMessages) {
            this.serverCall = serverCall;
            this.numMessages = numMessages;
        }

        @Override
        public Void get() {
            serverCall.observation.observeRequest(numMessages);
            serverCall.call.request(numMessages);
            return null;
        }
    }

    private static final class SendHeadersRunner<ReqT, RespT> implements Supplier<Void> {
        private final GrpcServerTelemetryCall<ReqT, RespT> serverCall;
        private final Metadata headers;

        public SendHeadersRunner(GrpcServerTelemetryCall<ReqT, RespT> serverCall, Metadata headers) {
            this.serverCall = serverCall;
            this.headers = headers;
        }

        @Override
        public Void get() {
            serverCall.observation.observeHeaders(headers);
            serverCall.call.sendHeaders(headers);
            return null;
        }
    }

    private static final class SendMessageRunner<ReqT, RespT> implements Supplier<Void> {
        private final GrpcServerTelemetryCall<ReqT, RespT> serverCall;
        private final RespT message;

        public SendMessageRunner(GrpcServerTelemetryCall<ReqT, RespT> serverCall, RespT message) {
            this.serverCall = serverCall;
            this.message = message;
        }

        @Override
        public Void get() {
            serverCall.observation.observeSendMessage(message);
            serverCall.call.sendMessage(message);
            return null;
        }
    }

    private static final class CloseRunner<ReqT, RespT> implements Supplier<Void> {
        private final GrpcServerTelemetryCall<ReqT, RespT> serverCall;
        private final Status status;
        private final Metadata trailers;

        public CloseRunner(GrpcServerTelemetryCall<ReqT, RespT> serverCall, Status status, Metadata trailers) {
            this.serverCall = serverCall;
            this.status = status;
            this.trailers = trailers;
        }

        @Override
        public Void get() {
            serverCall.observation.observeClose(status, trailers);
            try {
                serverCall.call.close(status, trailers);
            } finally {
                serverCall.observation.end();
            }
            return null;
        }
    }
}
