package ru.tinkoff.kora.grpc.server.telemetry;

import io.grpc.Status;
import jakarta.annotation.Nullable;

public interface GrpcServerMetrics {
    void onClose(@Nullable Status status, @Nullable Throwable exception, long processingTimeNano);

    void onSend(Object message);

    void onReceive(Object message);
}
