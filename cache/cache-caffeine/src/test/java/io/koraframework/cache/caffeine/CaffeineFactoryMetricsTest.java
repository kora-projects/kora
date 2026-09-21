package io.koraframework.cache.caffeine;

import io.koraframework.cache.caffeine.telemetry.CaffeineCacheTelemetryConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.when;

class CaffeineFactoryMetricsTest {

    @Test
    void cacheIsBuiltWhenMetricsAreEnabled() {
        var registry = new SimpleMeterRegistry();

        var metrics = Mockito.mock(CaffeineCacheTelemetryConfig.CaffeineCacheMetricsConfig.class);
        when(metrics.enabled()).thenReturn(true);
        when(metrics.tags()).thenReturn(Map.of());

        var telemetry = Mockito.mock(CaffeineCacheTelemetryConfig.class);
        when(telemetry.metrics()).thenReturn(metrics);

        var config = Mockito.mock(CaffeineCacheConfig.class);
        when(config.telemetry()).thenReturn(telemetry);
        when(config.enabled()).thenReturn(true);
        when(config.maximumSize()).thenReturn(100L);
        when(config.expireAfterAccess()).thenReturn(null);
        when(config.expireAfterWrite()).thenReturn(null);
        when(config.initialSize()).thenReturn(null);

        var cache = new CaffeineFactory(registry).<String, String>build("test", config);
        cache.put("key", "value");
        cache.getIfPresent("key");
        cache.getIfPresent("absent");

        assertFalse(registry.find("cache.gets").meters().isEmpty(), "cache.gets must be reported");
        assertFalse(registry.find("cache.puts").meters().isEmpty(), "cache.puts must be reported");
        assertFalse(registry.find("cache.size").meters().isEmpty(), "cache.size must be reported");
    }
}
