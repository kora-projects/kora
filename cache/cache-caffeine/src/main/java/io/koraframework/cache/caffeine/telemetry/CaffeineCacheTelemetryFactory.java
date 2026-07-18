package io.koraframework.cache.caffeine.telemetry;

public interface CaffeineCacheTelemetryFactory {

    CaffeineCacheTelemetry get(String cacheConfigPath, Class<?> cacheImpl, CaffeineCacheTelemetryConfig config);
}
