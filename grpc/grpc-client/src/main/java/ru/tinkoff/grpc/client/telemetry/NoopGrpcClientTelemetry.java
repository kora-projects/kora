package ru.tinkoff.grpc.client.telemetry;

import io.grpc.CallOptions;
import io.grpc.MethodDescriptor;

public class NoopGrpcClientTelemetry implements GrpcClientTelemetry {
    public static final NoopGrpcClientTelemetry INSTANCE = new NoopGrpcClientTelemetry();

    @Override
    public <ReqT, RespT> GrpcClientObservation observe(MethodDescriptor<ReqT, RespT> method, CallOptions callOptions) {
        return NoopGrpcClientObservation.INSTANCE;
    }
}
