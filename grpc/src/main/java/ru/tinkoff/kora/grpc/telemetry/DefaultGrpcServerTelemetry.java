package ru.tinkoff.kora.grpc.telemetry;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.Status;

import javax.annotation.Nullable;

public final class DefaultGrpcServerTelemetry implements GrpcServerTelemetry {
    private static final GrpcServerTelemetryContext NOOP_CTX = new GrpcServerTelemetryContext() {
        @Override
        public void close(Status status, Throwable exception) {

        }

        @Override
        public void sendMessage(Object message) {

        }

        @Override
        public void receiveMessage(Object message) {

        }
    };

    @Nullable
    private final GrpcServerMetricsFactory metrics;
    @Nullable
    private final GrpcServerTracer tracing;
    @Nullable
    private final GrpcServerLogger logger;

    public DefaultGrpcServerTelemetry(@Nullable GrpcServerMetricsFactory metrics, @Nullable GrpcServerTracer tracing, @Nullable GrpcServerLogger logger) {
        this.metrics = metrics;
        this.tracing = tracing;
        this.logger = logger;
    }

    @Override
    public GrpcServerTelemetryContext createContext(ServerCall<?, ?> call, Metadata headers) {
        var metrics = this.metrics;
        var tracing = this.tracing;
        var logger = this.logger;
        if (metrics == null && tracing == null && (logger == null || !logger.isEnabled())) {
            return DefaultGrpcServerTelemetry.NOOP_CTX;
        }

        var start = System.nanoTime();
        var serviceName = service(call);
        var methodName = method(call);
        var m = metrics == null ? null : metrics.get(call, headers, serviceName, methodName);
        var span = tracing == null ? null : tracing.createSpan(call, headers, serviceName, methodName);
        if (logger != null) {
            logger.logBegin(call, headers, serviceName, methodName);
        }
        return new DefaultGrpcServerTelemetryContext(start, serviceName, methodName, m, logger, span);
    }

    private String service(ServerCall<?, ?> call) {
        var fullMethodName = call.getMethodDescriptor().getFullMethodName();
        int slashIndex = fullMethodName.lastIndexOf('/');
        if (slashIndex == -1) {
            return "unknownService";
        }
        return fullMethodName.substring(0, slashIndex);
    }

    private String method(ServerCall<?, ?> call) {
        String fullMethodName = call.getMethodDescriptor().getFullMethodName();
        int slashIndex = fullMethodName.lastIndexOf('/');
        if (slashIndex == -1) {
            return "unknownMethod";
        }
        return fullMethodName.substring(slashIndex + 1);
    }
}
