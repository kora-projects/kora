package ru.tinkoff.kora.micrometer.module.http.server.tag;

public record DurationKey(int statusCode, String method, String target, String host, String scheme) {}
