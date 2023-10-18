package ru.tinkoff.kora.grpc.server.telemetry;

import io.grpc.Status;

public interface GrpcServerMetrics {
    void onClose(Status status, Throwable exception, long processingTime);

    void onSend(Object message);

    void onReceive(Object message);
}
