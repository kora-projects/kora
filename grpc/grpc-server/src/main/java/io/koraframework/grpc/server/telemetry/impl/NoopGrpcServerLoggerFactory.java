package io.koraframework.grpc.server.telemetry.impl;

import io.grpc.Metadata;
import io.grpc.Status;
import org.jspecify.annotations.Nullable;
import org.slf4j.helpers.NOPLogger;

public final class NoopGrpcServerLoggerFactory extends DefaultGrpcServerLoggerFactory {

    public static final NoopGrpcServerLoggerFactory INSTANCE = new NoopGrpcServerLoggerFactory();

    private NoopGrpcServerLoggerFactory() {}

    @Override
    public DefaultGrpcServerLogger create(DefaultGrpcServerTelemetry.TelemetryContext context) {
        return NoopGrpcServerLogger.INSTANCE;
    }

    public static final class NoopGrpcServerLogger extends DefaultGrpcServerLogger {

        public static final NoopGrpcServerLogger INSTANCE = new NoopGrpcServerLogger();

        private NoopGrpcServerLogger() {
            super(DefaultGrpcServerTelemetry.TelemetryContext.EMPTY, NOPLogger.NOP_LOGGER, NOPLogger.NOP_LOGGER);
        }

        @Override
        public boolean logRequestBody() {
            return false;
        }

        @Override
        public boolean logResponseBody() {
            return false;
        }

        @Override
        public void logRequest(String service, String method, Metadata requestHeaders, @Nullable Object requestMessage) {

        }

        @Override
        public void logResponse(String service,
                                String method,
                                @Nullable Status status,
                                @Nullable Throwable error,
                                @Nullable Object responseMessage,
                                long processingTimeNanos) {

        }
    }
}
