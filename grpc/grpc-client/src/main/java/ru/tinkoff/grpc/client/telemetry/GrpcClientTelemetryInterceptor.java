package ru.tinkoff.grpc.client.telemetry;

import io.grpc.*;
import org.jetbrains.annotations.Nullable;
import ru.tinkoff.kora.common.Context;

import java.util.concurrent.CancellationException;

public final class GrpcClientTelemetryInterceptor implements ClientInterceptor {
    private final GrpcClientTelemetry telemetry;

    public GrpcClientTelemetryInterceptor(GrpcClientTelemetry telemetry) {
        this.telemetry = telemetry;
    }

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
        var call = next.newCall(method, callOptions);
        return new MyClientCall<>(Context.current().fork(), method, call, telemetry);
    }

    private static final class MyClientCall<ReqT, RespT> extends ForwardingClientCall<ReqT, RespT> {
        private final Context ctx;
        private final MethodDescriptor<ReqT, RespT> method;
        private final ClientCall<ReqT, RespT> delegate;
        private final GrpcClientTelemetry telemetry;

        private volatile GrpcClientTelemetry.GrpcClientTelemetryCtx<ReqT, RespT> tctx;

        private MyClientCall(Context ctx, MethodDescriptor<ReqT, RespT> method, ClientCall<ReqT, RespT> delegate, GrpcClientTelemetry telemetry) {
            this.ctx = ctx;
            this.method = method;
            this.delegate = delegate;
            this.telemetry = telemetry;
        }

        @Override
        protected ClientCall<ReqT, RespT> delegate() {
            return this.delegate;
        }

        @Override
        public void start(Listener<RespT> responseListener, Metadata headers) {
            var oldCtx = Context.current();
            this.ctx.inject();
            this.tctx = this.telemetry.get(this.ctx, method, this.delegate, headers);
            try {
                this.delegate.start(new MyListener<>(responseListener, ctx, this.tctx), headers);
            } finally {
                oldCtx.inject();
            }
        }

        @Override
        public void sendMessage(ReqT message) {
            var oldCtx = Context.current();
            this.ctx.inject();
            var tctx = this.tctx.sendMessage(message);
            try {
                super.sendMessage(message);
                tctx.close();
            } catch (Exception e) {
                tctx.close(e);
                throw e;
            } finally {
                oldCtx.inject();
            }
        }

        @Override
        public void cancel(@Nullable String message, @Nullable Throwable cause) {
            var oldCtx = Context.current();
            this.ctx.inject();
            try {
                super.cancel(message, cause);
                if (cause != null) {
                    if (cause instanceof Exception ex) {
                        tctx.close(ex);
                    } else {
                        tctx.close(new RuntimeException(cause));
                    }
                } else {
                    if (message != null) {
                        tctx.close(new CancellationException(message));
                    } else {
                        tctx.close(new CancellationException());
                    }
                }
            } catch (Exception e) {
                tctx.close(e);
                throw e;
            } finally {
                oldCtx.inject();
            }
        }
    }

    private static class MyListener<ReqT, RespT> extends ClientCall.Listener<RespT> {
        private final ClientCall.Listener<RespT> responseListener;
        private final GrpcClientTelemetry.GrpcClientTelemetryCtx<ReqT, RespT> telemetry;
        private final Context ctx;

        public MyListener(ClientCall.Listener<RespT> responseListener, Context ctx, GrpcClientTelemetry.GrpcClientTelemetryCtx<ReqT, RespT> telemetry) {
            this.responseListener = responseListener;
            this.telemetry = telemetry;
            this.ctx = ctx;
        }

        @Override
        public void onMessage(RespT message) {
            var oldCtx = Context.current();
            this.ctx.inject();
            var tctx = this.telemetry.receiveMessage(message);
            try {
                responseListener.onMessage(message);
                tctx.close();
            } catch (Exception e) {
                tctx.close(e);
                throw e;
            } finally {
                oldCtx.inject();
            }
        }

        @Override
        public void onClose(Status status, Metadata trailers) {
            var oldCtx = Context.current();
            this.ctx.inject();
            try {
                responseListener.onClose(status, trailers);
            } finally {
                this.telemetry.close(status, trailers);
                oldCtx.inject();
            }
        }
    }
}
