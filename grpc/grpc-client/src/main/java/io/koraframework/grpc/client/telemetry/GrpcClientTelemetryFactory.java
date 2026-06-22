package io.koraframework.grpc.client.telemetry;

import io.grpc.ServiceDescriptor;

import java.net.URI;

public interface GrpcClientTelemetryFactory {

    GrpcClientTelemetry get(GrpcClientTelemetryConfig config, ServiceDescriptor service, URI uri);
}
