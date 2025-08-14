package ru.tinkoff.kora.micrometer.module.grpc.client.tag;

public record MessageReceivedKey(String serviceName, String methodName) {}
