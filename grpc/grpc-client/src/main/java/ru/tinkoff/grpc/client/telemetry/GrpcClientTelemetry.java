package ru.tinkoff.grpc.client.telemetry;

import io.grpc.CallOptions;
import io.grpc.MethodDescriptor;

public interface GrpcClientTelemetry {
    <ReqT, RespT> GrpcClientObservation observe(MethodDescriptor<ReqT, RespT> method, CallOptions callOptions);

}
