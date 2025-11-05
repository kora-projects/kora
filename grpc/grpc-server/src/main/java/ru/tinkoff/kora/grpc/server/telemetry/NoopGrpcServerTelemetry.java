package ru.tinkoff.kora.grpc.server.telemetry;

import io.grpc.Metadata;
import io.grpc.ServerCall;

public class NoopGrpcServerTelemetry implements GrpcServerTelemetry {
    public static final NoopGrpcServerTelemetry INSTANCE = new NoopGrpcServerTelemetry();

    @Override
    public GrpcServerObservation observe(ServerCall<?, ?> call, Metadata headers) {
        return NoopGrpcServerObservation.INSTANCE;
    }
}
