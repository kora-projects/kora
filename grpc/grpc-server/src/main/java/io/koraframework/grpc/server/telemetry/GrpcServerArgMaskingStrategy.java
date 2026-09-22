package io.koraframework.grpc.server.telemetry;

@FunctionalInterface
public interface GrpcServerArgMaskingStrategy {

    String mask(String key, Object value);
}
