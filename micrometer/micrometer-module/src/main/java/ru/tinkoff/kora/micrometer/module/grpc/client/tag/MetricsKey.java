package ru.tinkoff.kora.micrometer.module.grpc.client.tag;

import jakarta.annotation.Nullable;

public record MetricsKey(String serviceName,
                         String methodName,
                         @Nullable Integer code,
                         @Nullable Class<? extends Throwable> errorType) {}
