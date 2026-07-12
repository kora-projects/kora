package io.koraframework.cache.caffeine;

import io.koraframework.cache.caffeine.telemetry.CaffeineCacheTelemetryConfig;
import io.koraframework.cache.caffeine.telemetry.$CaffeineCacheTelemetryConfig_CaffeineCacheLoggingConfig_ConfigValueMapper;
import io.koraframework.cache.caffeine.telemetry.$CaffeineCacheTelemetryConfig_CaffeineCacheMetricsConfig_ConfigValueMapper;
import io.koraframework.cache.caffeine.telemetry.$CaffeineCacheTelemetryConfig_CaffeineCacheTracingConfig_ConfigValueMapper;
import io.koraframework.cache.caffeine.testdata.DummyCache;
import org.junit.jupiter.api.Assertions;
import org.mockito.Mockito;

import static org.mockito.Mockito.when;

abstract class CacheRunner extends Assertions implements CaffeineCacheModule {
    public static CaffeineCacheConfig getConfig() {
        var config = Mockito.mock(CaffeineCacheConfig.class);
        var telemetry = Mockito.mock(CaffeineCacheTelemetryConfig.class);
        when(config.telemetry()).thenReturn(telemetry);
        when(config.maximumSize()).thenReturn(100_000L);
        when(config.expireAfterAccess()).thenReturn(null);
        when(config.expireAfterWrite()).thenReturn(null);
        when(telemetry.metrics()).thenReturn(new $CaffeineCacheTelemetryConfig_CaffeineCacheMetricsConfig_ConfigValueMapper.CaffeineCacheMetricsConfig_Defaults());
        when(telemetry.logging()).thenReturn(new $CaffeineCacheTelemetryConfig_CaffeineCacheLoggingConfig_ConfigValueMapper.CaffeineCacheLoggingConfig_Defaults());
        when(telemetry.tracing()).thenReturn(new $CaffeineCacheTelemetryConfig_CaffeineCacheTracingConfig_ConfigValueMapper.CaffeineCacheTracingConfig_Defaults());
        return config;
    }
    protected DummyCache createCache() {
        try {
            return new DummyCache(getConfig(), caffeineCacheFactory(null), defaultCaffeineCacheTelemetryFactory(null, null, null, null));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
