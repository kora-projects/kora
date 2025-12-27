package ru.tinkoff.kora.cache.telemetry;


public interface CacheTelemetryOperation {
    String name();

    String cacheName();

    String origin();
}
