package ru.tinkoff.kora.grpc.interceptors;

import io.grpc.*;
import ru.tinkoff.kora.grpc.telemetry.GrpcServerTelemetry;

import java.util.concurrent.CancellationException;

public class TelemetryInterceptor implements ServerInterceptor {
    private final GrpcServerTelemetry telemetry;

    public TelemetryInterceptor(GrpcServerTelemetry telemetry) {
        this.telemetry = telemetry;
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        var ctx = this.telemetry.createContext(call, headers);
        var c = new TelemetryServerCall<>(call, ctx);
        var listener = next.startCall(c, headers);
        return new TelemetryServerCallListener<>(listener, ctx);
    }


    private static final class TelemetryServerCall<REQUEST, RESPONSE> extends ForwardingServerCall.SimpleForwardingServerCall<REQUEST, RESPONSE> {
        private final GrpcServerTelemetry.GrpcServerTelemetryContext telemetryContext;

        private TelemetryServerCall(ServerCall<REQUEST, RESPONSE> delegate, GrpcServerTelemetry.GrpcServerTelemetryContext telemetryContext) {
            super(delegate);
            this.telemetryContext = telemetryContext;
        }

        @Override
        public void sendMessage(RESPONSE message) {
            this.telemetryContext.sendMessage(message);
            super.sendMessage(message);
        }

        @Override
        public void close(Status status, Metadata trailers) {
            try {
                delegate().close(status, trailers);
            } catch (Throwable e) {
                this.telemetryContext.close(null, e);
                throw e;
            }
            this.telemetryContext.close(status, status.getCause());
        }
    }

    private static final class TelemetryServerCallListener<REQUEST> extends ForwardingServerCallListener.SimpleForwardingServerCallListener<REQUEST> {
        private final GrpcServerTelemetry.GrpcServerTelemetryContext telemetryContext;

        private TelemetryServerCallListener(ServerCall.Listener<REQUEST> delegate, GrpcServerTelemetry.GrpcServerTelemetryContext telemetryContext) {
            super(delegate);
            this.telemetryContext = telemetryContext;
        }

        @Override
        public void onMessage(REQUEST message) {
            this.telemetryContext.receiveMessage(message);
            delegate().onMessage(message);
        }

        @Override
        public void onHalfClose() {
            try {
                delegate().onHalfClose();
            } catch (Throwable e) {
                this.telemetryContext.close(null, e);
                throw e;
            }
        }

        @Override
        public void onCancel() {
            try {
                delegate().onCancel();
            } catch (Throwable e) {
                this.telemetryContext.close(null, e);
                throw e;
            }
            this.telemetryContext.close(null, new CancellationException());
        }

        @Override
        public void onComplete() {
            try {
                delegate().onComplete();
            } catch (Throwable e) {
                this.telemetryContext.close(null, e);
                throw e;
            }
        }

        @Override
        public void onReady() {
            try {
                delegate().onReady();
            } catch (Throwable e) {
                this.telemetryContext.close(null, e);
                throw e;
            }
        }
    }
}
