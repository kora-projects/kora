package ru.tinkoff.kora.grpc.telemetry;

import io.grpc.Metadata;
import io.grpc.ServerCall;

import javax.annotation.Nullable;

public final class DefaultGrpcServerTelemetry implements GrpcServerTelemetry {
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
        var start = System.nanoTime();
        var serviceName = service(call);
        var methodName = method(call);
        var metrics = this.metrics == null ? null : this.metrics.get(call, headers, serviceName, methodName);
        var span = this.tracing == null ? null : this.tracing.createSpan(call, headers, serviceName, methodName);
        if (this.logger != null) this.logger.logBegin(call, headers, serviceName, methodName);
        return new DefaultGrpcServerTelemetryContext(start, serviceName, methodName, metrics, logger, span);
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
