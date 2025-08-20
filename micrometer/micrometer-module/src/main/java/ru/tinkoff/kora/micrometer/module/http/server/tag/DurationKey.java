package ru.tinkoff.kora.micrometer.module.http.server.tag;

import jakarta.annotation.Nullable;

public record DurationKey(int statusCode, String method, String route, String host, int port, String scheme, @Nullable Class<? extends Throwable> errorType) {}
