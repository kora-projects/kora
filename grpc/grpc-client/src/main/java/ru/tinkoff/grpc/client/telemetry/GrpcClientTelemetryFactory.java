package ru.tinkoff.grpc.client.telemetry;

import io.grpc.ServiceDescriptor;

import java.net.URI;

public interface GrpcClientTelemetryFactory {
    GrpcClientTelemetry get(ServiceDescriptor service, GrpcClientTelemetryConfig telemetryConfig, URI uri);
}
