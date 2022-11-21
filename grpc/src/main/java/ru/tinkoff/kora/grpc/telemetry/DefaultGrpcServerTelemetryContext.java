package ru.tinkoff.kora.grpc.telemetry;

import io.grpc.Status;
import org.jetbrains.annotations.Nullable;

public final class DefaultGrpcServerTelemetryContext implements GrpcServerTelemetry.GrpcServerTelemetryContext {
    private final String serviceName;
    private final String methodName;
    private final long startTime;
    private final GrpcServerMetrics metrics;
    private final GrpcServerLogger logger;
    private final GrpcServerTracer.GrpcServerSpan span;

    public DefaultGrpcServerTelemetryContext(long startTime, String serviceName, String methodName, @Nullable GrpcServerMetrics metrics, @Nullable GrpcServerLogger logger, @Nullable GrpcServerTracer.GrpcServerSpan span) {
        this.startTime = startTime;
        this.metrics = metrics;
        this.logger = logger;
        this.span = span;
        this.serviceName = serviceName;
        this.methodName = methodName;
    }

    @Override
    public void close(@Nullable Status status, @Nullable Throwable exception) {
        var processingTime = System.nanoTime() - this.startTime;
        if (this.logger != null) this.logger.logEnd(this.serviceName, this.methodName, status, exception, processingTime);
        if (this.metrics != null) this.metrics.onClose(status, exception, processingTime);
        if (this.span != null) this.span.close(status, exception, processingTime);
    }

    @Override
    public void sendMessage(Object message) {
        if (this.logger != null) this.logger.logSendMessage(this.serviceName, this.methodName, message);
        if (this.metrics != null) this.metrics.onSend(message);
        if (this.span != null) this.span.addSend(message);
    }

    @Override
    public void receiveMessage(Object message) {
        if (this.logger != null) this.logger.logReceiveMessage(this.serviceName, this.methodName, message);
        if (this.metrics != null) this.metrics.onReceive(message);
        if (this.span != null) this.span.addReceive(message);
    }
}
