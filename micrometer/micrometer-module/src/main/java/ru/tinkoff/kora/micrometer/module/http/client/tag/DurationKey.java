package ru.tinkoff.kora.micrometer.module.http.client.tag;

import jakarta.annotation.Nullable;

public record DurationKey(int statusCode, String method, @Nullable String host, @Nullable String scheme, String target, @Nullable Class<? extends Throwable> errorType) {}
