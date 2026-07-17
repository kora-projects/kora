package io.koraframework.grpc.server.telemetry;

public interface GrpcServerTelemetryFactory {

    GrpcServerTelemetry get(String name, int port, GrpcServerTelemetryConfig config);
}
