package ru.tinkoff.kora.grpc.server.interceptors;

import io.grpc.*;
import ru.tinkoff.kora.application.graph.ValueOf;
import ru.tinkoff.kora.grpc.server.telemetry.GrpcServerTelemetry;

public class TelemetryInterceptor implements ServerInterceptor {
    private final ValueOf<GrpcServerTelemetry> telemetry;

    public TelemetryInterceptor(ValueOf<GrpcServerTelemetry> telemetry) {
        this.telemetry = telemetry;
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        var ctx = this.telemetry.get().createContext(call, headers);
        var c = new TelemetryServerCall<>(call, ctx);
        try {
            var listener = next.startCall(c, headers);
            return new TelemetryServerCallListener<>(listener, ctx);
        } catch (StatusRuntimeException e) {
            ctx.close(e.getStatus(), e);
            throw e;
        } catch (Exception e) {
            ctx.close(null, e);
            throw e;
        }
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
            } catch (StatusRuntimeException e) {
                this.telemetryContext.close(e.getStatus(), e);
                throw e;
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
            } catch (StatusRuntimeException e) {
                this.telemetryContext.close(e.getStatus(), e);
                throw e;
            } catch (Throwable e) {
                this.telemetryContext.close(null, e);
                throw e;
            }
        }

        @Override
        public void onCancel() {
            try {
                delegate().onCancel();
            } catch (StatusRuntimeException e) {
                this.telemetryContext.close(e.getStatus(), e);
                throw e;
            } catch (Throwable e) {
                this.telemetryContext.close(null, e);
                throw e;
            }
        }

        @Override
        public void onComplete() {
            try {
                delegate().onComplete();
            } catch (StatusRuntimeException e) {
                this.telemetryContext.close(e.getStatus(), e);
                throw e;
            } catch (Throwable e) {
                this.telemetryContext.close(null, e);
                throw e;
            }
        }

        @Override
        public void onReady() {
            try {
                delegate().onReady();
            } catch (StatusRuntimeException e) {
                this.telemetryContext.close(e.getStatus(), e);
                throw e;
            } catch (Throwable e) {
                this.telemetryContext.close(null, e);
                throw e;
            }
        }
    }
}
