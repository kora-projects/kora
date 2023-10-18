package ru.tinkoff.kora.grpc.server.telemetry;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.Status;
import jakarta.annotation.Nullable;

public interface GrpcServerTelemetry {
    GrpcServerTelemetryContext createContext(ServerCall<?, ?> call, Metadata headers);

    interface GrpcServerTelemetryContext {
        void close(@Nullable Status status, @Nullable Throwable exception);

        void sendMessage(Object message);

        void receiveMessage(Object message);
    }
}
