package ru.tinkoff.kora.micrometer.module.grpc.client.tag;

public record MessageSendKey(String serviceName, String methodName) {}
