package io.koraframework.grpc.client.telemetry.impl;

import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import org.jspecify.annotations.Nullable;
import org.slf4j.helpers.NOPLogger;

public final class NoopGrpcClientLoggerFactory extends DefaultGrpcClientLoggerFactory {

    public static final NoopGrpcClientLoggerFactory INSTANCE = new NoopGrpcClientLoggerFactory();

    private NoopGrpcClientLoggerFactory() {}

    @Override
    public DefaultGrpcClientLogger create(DefaultGrpcClientTelemetry.TelemetryContext context) {
        return NoopGrpcClientLogger.INSTANCE;
    }

    public static final class NoopGrpcClientLogger extends DefaultGrpcClientLogger {

        public static final NoopGrpcClientLogger INSTANCE = new NoopGrpcClientLogger();

        private NoopGrpcClientLogger() {
            super(NOPLogger.NOP_LOGGER, NOPLogger.NOP_LOGGER, DefaultGrpcClientTelemetry.TelemetryContext.EMPTY);
        }

        @Override
        public void logRequest(MethodDescriptor<?, ?> method, Metadata requestHeaders) {

        }

        @Override
        public void logResponse(MethodDescriptor<?, ?> method, @Nullable Status status, @Nullable Throwable error, long processingTimeNanos) {

        }
    }
}
