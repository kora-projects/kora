package io.koraframework.cache.caffeine;

import com.github.benmanes.caffeine.cache.Cache;
import io.koraframework.application.graph.ApplicationGraphDraw;
import io.koraframework.cache.caffeine.telemetry.CaffeineCacheTelemetryConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class CaffeineRefreshMetricsTest {

    static CaffeineCacheConfig config(long maximumSize) {
        var metrics = Mockito.mock(CaffeineCacheTelemetryConfig.CaffeineCacheMetricsConfig.class);
        when(metrics.enabled()).thenReturn(true);
        when(metrics.tags()).thenReturn(Map.of());
        var telemetry = Mockito.mock(CaffeineCacheTelemetryConfig.class);
        when(telemetry.metrics()).thenReturn(metrics);
        var config = Mockito.mock(CaffeineCacheConfig.class);
        when(config.telemetry()).thenReturn(telemetry);
        when(config.enabled()).thenReturn(true);
        when(config.maximumSize()).thenReturn(maximumSize);
        when(config.expireAfterAccess()).thenReturn(null);
        when(config.expireAfterWrite()).thenReturn(null);
        when(config.initialSize()).thenReturn(null);
        return config;
    }

    @Test
    void metricsFollowCacheRebuiltByConfigRefresh() throws Exception {
        var registry = new SimpleMeterRegistry();
        var factory = new CaffeineFactory(registry);
        var maxSize = new AtomicLong(100);

        var draw = new ApplicationGraphDraw(CaffeineRefreshMetricsTest.class);
        var configNode = draw.addNode(CaffeineCacheConfig.class, null, null, List.of(), List.of(), List.of(), _ -> config(maxSize.get()));
        var cacheNode = draw.addNode(Cache.class, null, null, List.of(configNode), List.of(configNode), List.of(),
            g -> factory.<String, String>build("test", g.get(configNode)));
        var graph = draw.init();
        try {
            maxSize.set(200);
            graph.refresh(configNode);
            @SuppressWarnings("unchecked")
            var cache = (Cache<String, String>) graph.get(cacheNode);
            cache.put("a", "1");
            cache.put("b", "2");
            cache.put("c", "3");
            cache.getIfPresent("a");
            System.gc();

            assertThat(registry.get("cache.size").tag("cache", "test").gauge().value()).as("cache.size of the current cache").isEqualTo(3.0);
            assertThat(registry.get("cache.gets").tag("cache", "test").tag("result", "hit").functionCounter().count()).as("hits of the current cache").isEqualTo(1.0);
        } finally {
            graph.release();
        }
    }
}
