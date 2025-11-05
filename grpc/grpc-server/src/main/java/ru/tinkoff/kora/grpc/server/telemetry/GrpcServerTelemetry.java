package ru.tinkoff.kora.grpc.server.telemetry;

import io.grpc.Metadata;
import io.grpc.ServerCall;

public interface GrpcServerTelemetry {
    GrpcServerObservation observe(ServerCall<?, ?> call, Metadata headers);
}
