package io.koraframework.grpc.client.telemetry.impl;

import io.grpc.MethodDescriptor;
import io.grpc.Status;
import org.jspecify.annotations.Nullable;

public final class NoopGrpcClientMetricsFactory extends DefaultGrpcClientMetricsFactory {

    public static final NoopGrpcClientMetricsFactory INSTANCE = new NoopGrpcClientMetricsFactory();

    private NoopGrpcClientMetricsFactory() {}

    @Override
    public DefaultGrpcClientMetrics create(DefaultGrpcClientTelemetry.TelemetryContext context) {
        return NoopGrpcClientMetrics.INSTANCE;
    }

    public static final class NoopGrpcClientMetrics extends DefaultGrpcClientMetrics {

        public static final NoopGrpcClientMetrics INSTANCE = new NoopGrpcClientMetrics();

        private NoopGrpcClientMetrics() {
            super(DefaultGrpcClientTelemetry.TelemetryContext.EMPTY);
        }

        @Override
        public void record(MethodDescriptor<?, ?> method, @Nullable Status status, @Nullable Throwable error, long processingTimeNanos) {

        }
    }
}
