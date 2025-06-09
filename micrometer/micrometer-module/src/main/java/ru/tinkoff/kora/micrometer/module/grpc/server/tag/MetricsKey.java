package ru.tinkoff.kora.micrometer.module.grpc.server.tag;

public record MetricsKey(String serviceName, String methodName) {}
