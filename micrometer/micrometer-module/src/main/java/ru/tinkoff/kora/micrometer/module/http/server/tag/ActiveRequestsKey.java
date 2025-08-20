package ru.tinkoff.kora.micrometer.module.http.server.tag;

public record ActiveRequestsKey(String method, String target, String host, String scheme) {}
