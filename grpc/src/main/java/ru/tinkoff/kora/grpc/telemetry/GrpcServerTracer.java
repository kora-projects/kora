package ru.tinkoff.kora.grpc.telemetry;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.Status;

import javax.annotation.Nullable;

public interface GrpcServerTracer {
    interface GrpcServerSpan {
        void close(Status status, @Nullable Throwable exception);

        void addSend(Object message);

        void addReceive(Object message);
    }

    GrpcServerSpan createSpan(ServerCall<?, ?> call, Metadata headers, String serviceName, String methodName);
}
