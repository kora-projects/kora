package io.koraframework.cache.caffeine;

import io.koraframework.cache.caffeine.telemetry.CaffeineCacheTelemetryConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import io.koraframework.cache.caffeine.testdata.DummyCache;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    @Test
    void computeIfAbsentRecordsLoadMetrics() {
        var registry = new SimpleMeterRegistry();

        var metrics = Mockito.mock(CaffeineCacheTelemetryConfig.CaffeineCacheMetricsConfig.class);
        when(metrics.enabled()).thenReturn(true);
        when(metrics.tags()).thenReturn(Map.of());
        var config = CacheRunner.getConfig();
        when(config.telemetry().metrics()).thenReturn(metrics);

        var module = new CaffeineCacheModule() {};
        var cache = new DummyCache(config, module.caffeineCacheFactory(registry), module.defaultCaffeineCacheTelemetryFactory(null, null, null, null));
        cache.computeIfAbsent("1", k -> "1");
        cache.computeIfAbsent("1", k -> "2");
        cache.computeIfAbsent("2", k -> null);
        assertThrows(IllegalArgumentException.class, () -> cache.computeIfAbsent("3", k -> {
            throw new IllegalArgumentException("boom");
        }));

        assertEquals(1.0, registry.get("cache.gets").tag("result", "hit").functionCounter().count());
        assertEquals(3.0, registry.get("cache.gets").tag("result", "miss").functionCounter().count());
        // CaffeineCacheMetrics reports Caffeine loadCount (successful + failed loads) as cache.puts
        assertEquals(3.0, registry.get("cache.puts").functionCounter().count());
    }

    @Test
    void computeIfAbsentConcurrentWaitersRecordHits() throws Exception {
        var registry = new SimpleMeterRegistry();

        var metrics = Mockito.mock(CaffeineCacheTelemetryConfig.CaffeineCacheMetricsConfig.class);
        when(metrics.enabled()).thenReturn(true);
        when(metrics.tags()).thenReturn(Map.of());
        var config = CacheRunner.getConfig();
        when(config.telemetry().metrics()).thenReturn(metrics);

        var module = new CaffeineCacheModule() {};
        var cache = new DummyCache(config, module.caffeineCacheFactory(registry), module.defaultCaffeineCacheTelemetryFactory(null, null, null, null));
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(4)) {
            var loader = executor.submit(() -> cache.computeIfAbsent("1", k -> {
                started.countDown();
                await(release);
                return "1";
            }));
            assertTrue(started.await(10, TimeUnit.SECONDS));
            var waiters = new ArrayList<Future<String>>();
            for (int i = 0; i < 3; i++) {
                waiters.add(executor.submit(() -> cache.computeIfAbsent("1", k -> "2")));
            }
            Thread.sleep(200);
            release.countDown();
            assertEquals("1", loader.get(10, TimeUnit.SECONDS));
            for (var waiter : waiters) {
                assertEquals("1", waiter.get(10, TimeUnit.SECONDS));
            }
        }

        assertEquals(3.0, registry.get("cache.gets").tag("result", "hit").functionCounter().count());
        assertEquals(1.0, registry.get("cache.gets").tag("result", "miss").functionCounter().count());
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(10, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }
}
