package io.koraframework.grpc.client.telemetry.impl;

import io.grpc.CallOptions;
import io.grpc.MethodDescriptor;
import io.koraframework.grpc.client.telemetry.GrpcClientObservation;
import io.koraframework.grpc.client.telemetry.GrpcClientTelemetry;

public final class NoopGrpcClientTelemetry implements GrpcClientTelemetry {

    public static final NoopGrpcClientTelemetry INSTANCE = new NoopGrpcClientTelemetry();

    private NoopGrpcClientTelemetry() {}

    @Override
    public <ReqT, RespT> GrpcClientObservation observe(MethodDescriptor<ReqT, RespT> method, CallOptions callOptions) {
        return NoopGrpcClientObservation.INSTANCE;
    }
}
