package ru.tinkoff.kora.grpc.telemetry;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.Status;

import javax.annotation.Nullable;

public interface GrpcServerLogger {

    void logEnd(String serviceName, String methodName, @Nullable Status status, @Nullable Throwable exception, long processingTime);

    void logBegin(ServerCall<?, ?> call, Metadata headers, String serviceName, String methodName);

    void logSendMessage(String serviceName, String methodName, Object message);

    void logReceiveMessage(String serviceName, String methodName, Object message);
}
