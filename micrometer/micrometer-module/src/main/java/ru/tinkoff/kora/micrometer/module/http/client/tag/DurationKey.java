package ru.tinkoff.kora.micrometer.module.http.client.tag;

import jakarta.annotation.Nullable;

public record DurationKey(int statusCode, String method, String host, String scheme, String target, @Nullable Class<? extends Throwable> errorType) {}
