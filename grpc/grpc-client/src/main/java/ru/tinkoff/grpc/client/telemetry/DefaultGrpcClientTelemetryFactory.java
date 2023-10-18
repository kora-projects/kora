package ru.tinkoff.grpc.client.telemetry;

import io.grpc.*;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.net.URI;

public final class DefaultGrpcClientTelemetryFactory implements GrpcClientTelemetryFactory {
    @Nullable
    private final GrpcClientMetricsFactory metrics;
    @Nullable
    private final GrpcClientTracerFactory tracer;
    @Nullable
    private final GrpcClientLoggerFactory logger;

    public DefaultGrpcClientTelemetryFactory(@Nullable GrpcClientMetricsFactory metrics, @Nullable GrpcClientTracerFactory tracer, @Nullable GrpcClientLoggerFactory logger) {
        this.metrics = metrics;
        this.tracer = tracer;
        this.logger = logger;
    }

    @Override
    public GrpcClientTelemetry get(ServiceDescriptor service, TelemetryConfig telemetryConfig, URI uri) {
        var metrics = this.metrics == null ? null : this.metrics.get(service, telemetryConfig.metrics(), uri);
        var tracer = this.tracer == null ? null : this.tracer.get(service, telemetryConfig.tracing(), uri);
        var logger = this.logger == null ? null : this.logger.get(service, telemetryConfig.logging(), uri);
        if (metrics == null && tracer == null && (logger == null || !logger.enabled())) {
            return null;
        }
        return new DefaultGrpcClientTelemetry(metrics, tracer, logger, service, uri);
    }

    private static final class DefaultGrpcClientTelemetry implements GrpcClientTelemetry {
        @Nullable
        private final GrpcClientMetrics metrics;
        @Nullable
        private final GrpcClientTracer tracer;
        @Nullable
        private final GrpcClientLogger logger;
        private final ServiceDescriptor service;
        private final URI uri;

        public DefaultGrpcClientTelemetry(@Nullable GrpcClientMetrics metrics, @Nullable GrpcClientTracer tracer, @Nullable GrpcClientLogger logger, ServiceDescriptor service, URI uri) {
            this.metrics = metrics;
            this.tracer = tracer;
            this.logger = logger;
            this.service = service;
            this.uri = uri;
        }

        @Override
        public <ReqT, RespT> GrpcClientTelemetryCtx<ReqT, RespT> get(Context ctx, MethodDescriptor<ReqT, RespT> method, ClientCall<ReqT, RespT> call, Metadata headers) {
            var startTime = System.nanoTime();
            var span = this.tracer == null ? null : this.tracer.callSpan(ctx, method, uri, call, headers);
            if (logger != null && logger.enabled()) {
                logger.logCall(ctx, method, uri);
            }

            return new DefaultGrpcClientTelemetryCtx<>(ctx, startTime, method, metrics, logger, span);
        }
    }

    private static final class DefaultGrpcClientTelemetryCtx<ReqT, RespT> implements GrpcClientTelemetry.GrpcClientTelemetryCtx<ReqT, RespT> {

        private final long startTime;
        private final MethodDescriptor<ReqT, RespT> method;
        @Nullable
        private final GrpcClientMetrics metrics;
        @Nullable
        private final GrpcClientLogger logger;
        @Nullable
        private final GrpcClientTracer.GrpcClientSpan span;
        private final Context ctx;

        public DefaultGrpcClientTelemetryCtx(Context ctx, long startTime, MethodDescriptor<ReqT, RespT> method, GrpcClientMetrics metrics, GrpcClientLogger logger, GrpcClientTracer.GrpcClientSpan span) {
            this.ctx = ctx;
            this.startTime = startTime;
            this.method = method;
            this.metrics = metrics;
            this.logger = logger;
            this.span = span;
        }

        @Override
        public void close(Status status, Metadata trailers) {
            if (logger != null) {
                this.logger.logEnd(method, startTime, status, trailers);
            }
            if (span != null) {
                span.close(status, trailers);
            }
            if (metrics != null) {
                metrics.recordEnd(method, startTime, status, trailers);
            }
        }

        @Override
        public void close(Exception e) {
            if (logger != null) {
                this.logger.logEnd(method, startTime, e);
            }
            if (span != null) {
                span.close(e);
            }
            if (metrics != null) {
                metrics.recordEnd(method, startTime, e);
            }
        }

        @Override
        public GrpcClientTelemetry.GrpcClientSendMessageTelemetryCtx<ReqT, RespT> sendMessage(ReqT message) {
            var span = this.span == null ? null : this.span.reqSpan(ctx, method, message);
            if (this.logger != null) {
                this.logger.logSendMessage(method, message);
            }
            if (this.metrics != null) {
                this.metrics.recordSendMessage(method, message);
            }
            return new DefaultGrpcClientSendMessageTelemetryCtx<>(span);
        }

        @Override
        public GrpcClientTelemetry.GrpcClientReceiveMessageTelemetryCtx<ReqT, RespT> receiveMessage(RespT message) {
            var span = this.span == null ? null : this.span.resSpan(ctx, method, message);
            if (this.logger != null) {
                this.logger.logReceiveMessage(method, message);
            }
            if (this.metrics != null) {
                this.metrics.recordReceiveMessage(method, message);
            }
            return new DefaultGrpcClientReceiveMessageTelemetryCtx<>(span);
        }
    }

    private static final class DefaultGrpcClientSendMessageTelemetryCtx<ReqT, RespT> implements GrpcClientTelemetry.GrpcClientSendMessageTelemetryCtx<ReqT, RespT> {
        @Nullable
        private final GrpcClientTracer.GrpcClientRequestSpan span;

        public DefaultGrpcClientSendMessageTelemetryCtx(@Nullable GrpcClientTracer.GrpcClientRequestSpan span) {
            this.span = span;
        }

        @Override
        public void close(Exception e) {
            if (span != null) {
                span.close(e);
            }
        }

        @Override
        public void close() {
            if (span != null) {
                span.close();
            }
        }
    }

    private static final class DefaultGrpcClientReceiveMessageTelemetryCtx<ReqT, RespT> implements GrpcClientTelemetry.GrpcClientReceiveMessageTelemetryCtx<ReqT, RespT> {
        private final GrpcClientTracer.GrpcClientResponseSpan span;

        public DefaultGrpcClientReceiveMessageTelemetryCtx(GrpcClientTracer.GrpcClientResponseSpan span) {
            this.span = span;
        }

        @Override
        public void close(Exception e) {
            if (span != null) {
                span.close(e);
            }
        }

        @Override
        public void close() {
            if (span != null) {
                span.close();
            }
        }
    }
}
