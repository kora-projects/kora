package io.koraframework.grpc.server.telemetry.impl;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.koraframework.grpc.server.telemetry.GrpcServerObservation;
import io.koraframework.grpc.server.telemetry.GrpcServerTelemetry;

public final class NoopGrpcServerTelemetry implements GrpcServerTelemetry {

    public static final NoopGrpcServerTelemetry INSTANCE = new NoopGrpcServerTelemetry();

    private NoopGrpcServerTelemetry() {}

    @Override
    public GrpcServerObservation observe(ServerCall<?, ?> call, Metadata headers) {
        return NoopGrpcServerObservation.INSTANCE;
    }
}
