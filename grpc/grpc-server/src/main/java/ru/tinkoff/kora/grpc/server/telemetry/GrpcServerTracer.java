package ru.tinkoff.kora.grpc.server.telemetry;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.Status;
import jakarta.annotation.Nullable;

public interface GrpcServerTracer {
    interface GrpcServerSpan {
        void close(@Nullable Status status, @Nullable Throwable exception);

        void addSend(Object message);

        void addReceive(Object message);
    }

    GrpcServerSpan createSpan(ServerCall<?, ?> call, Metadata headers, String serviceName, String methodName);
}
