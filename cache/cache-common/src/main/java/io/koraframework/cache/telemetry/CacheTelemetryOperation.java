package io.koraframework.cache.telemetry;


public interface CacheTelemetryOperation {
    String name();

    String cacheName();

    String origin();
}
