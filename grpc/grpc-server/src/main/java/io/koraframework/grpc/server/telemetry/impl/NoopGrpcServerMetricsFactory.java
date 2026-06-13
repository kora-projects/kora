package io.koraframework.grpc.server.telemetry.impl;

import io.grpc.Status;
import org.jspecify.annotations.Nullable;

public final class NoopGrpcServerMetricsFactory extends DefaultGrpcServerMetricsFactory {

    public static final NoopGrpcServerMetricsFactory INSTANCE = new NoopGrpcServerMetricsFactory();

    private NoopGrpcServerMetricsFactory() {}

    @Override
    public DefaultGrpcServerMetrics create(DefaultGrpcServerTelemetry.TelemetryContext context) {
        return NoopGrpcServerMetrics.INSTANCE;
    }

    public static final class NoopGrpcServerMetrics extends DefaultGrpcServerMetrics {

        public static final NoopGrpcServerMetrics INSTANCE = new NoopGrpcServerMetrics();

        private NoopGrpcServerMetrics() {
            super(DefaultGrpcServerTelemetry.TelemetryContext.EMPTY);
        }

        @Override
        public void record(String service, String method, @Nullable Status status, long processingTimeNanos) {

        }
    }
}
