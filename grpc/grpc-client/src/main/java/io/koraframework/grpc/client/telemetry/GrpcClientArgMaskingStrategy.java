package io.koraframework.grpc.client.telemetry;

@FunctionalInterface
public interface GrpcClientArgMaskingStrategy {

    String mask(String key, Object value);
}
