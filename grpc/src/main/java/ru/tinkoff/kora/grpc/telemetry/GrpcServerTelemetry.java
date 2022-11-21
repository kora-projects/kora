package ru.tinkoff.kora.grpc.telemetry;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.Status;

import javax.annotation.Nullable;

public interface GrpcServerTelemetry {
    GrpcServerTelemetryContext createContext(ServerCall<?, ?> call, Metadata headers);

    interface GrpcServerTelemetryContext {
        void close(@Nullable Status status, @Nullable Throwable exception);

        void sendMessage(Object message);

        void receiveMessage(Object message);
    }
}
