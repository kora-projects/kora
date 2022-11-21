package ru.tinkoff.kora.cache.caffeine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import ru.tinkoff.kora.cache.caffeine.testdata.CacheableMockLifecycle;
import ru.tinkoff.kora.cache.caffeine.testdata.CacheableTargetMono;

import java.math.BigDecimal;
import java.time.Duration;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MonoCacheAopTests extends CacheRunner {

    private CacheableTargetMono service = null;

    private CacheableTargetMono getService() {
        if (service != null) {
            return service;
        }

        var graphDraw = createGraphDraw();
        var graph = graphDraw.init().block();
        var values = graphDraw.getNodes()
            .stream()
            .map(graph::get)
            .toList();

        service = values.stream()
            .filter(a -> a instanceof CacheableMockLifecycle)
            .map(a -> ((CacheableMockLifecycle) a).mono())
            .findFirst().orElseThrow();
        return service;
    }

    @BeforeEach
    void reset() {
        if (service != null) {
            service.evictAll().block(Duration.ofSeconds(1));
        }
    }

    @Test
    void getFromCacheWhenWasCacheEmpty() {
        // given
        final CacheableTargetMono service = getService();

        service.value = "1";
        assertNotNull(service);

        // when
        final String notCached = service.getValue("1", BigDecimal.ZERO).block(Duration.ofMinutes(1));
        service.value = "2";

        // then
        final String fromCache = service.getValue("1", BigDecimal.ZERO).block(Duration.ofMinutes(1));
        assertEquals(notCached, fromCache);
        assertNotEquals("2", fromCache);
    }

    @Test
    void getFromCacheWhenCacheFilled() {
        // given
        final CacheableTargetMono service = getService();

        service.value = "1";
        assertNotNull(service);

        // when
        final String initial = service.getValue("1", BigDecimal.ZERO).block(Duration.ofMinutes(1));
        final String cached = service.putValue(BigDecimal.ZERO, "5", "1").block(Duration.ofMinutes(1));
        assertEquals(initial, cached);
        service.value = "2";

        // then
        final String fromCache = service.getValue("1", BigDecimal.ZERO).block(Duration.ofMinutes(1));
        assertEquals(cached, fromCache);
    }

    @Test
    void getFromCacheWrongKeyWhenCacheFilled() {
        // given
        final CacheableTargetMono service = getService();

        service.value = "1";
        assertNotNull(service);

        // when
        final String initial = service.getValue("1", BigDecimal.ZERO).block(Duration.ofMinutes(1));
        final String cached = service.putValue(BigDecimal.ZERO, "5", "1").block(Duration.ofMinutes(1));
        assertEquals(initial, cached);
        service.value = "2";

        // then
        final String fromCache = service.getValue("2", BigDecimal.ZERO).block(Duration.ofMinutes(1));
        assertNotEquals(cached, fromCache);
        assertEquals(service.value, fromCache);
    }

    @Test
    void getFromCacheWhenCacheFilledOtherKey() {
        // given
        final CacheableTargetMono service = getService();

        service.value = "1";
        assertNotNull(service);

        // when
        final String cached = service.putValue(BigDecimal.ZERO, "5", "1").block(Duration.ofMinutes(1));
        service.value = "2";
        final String initial = service.getValue("2", BigDecimal.ZERO).block(Duration.ofMinutes(1));
        assertNotEquals(cached, initial);

        // then
        final String fromCache = service.getValue("2", BigDecimal.ZERO).block(Duration.ofMinutes(1));
        assertNotEquals(cached, fromCache);
        assertEquals(initial, fromCache);
    }

    @Test
    void getFromCacheWhenCacheInvalidate() {
        // given
        final CacheableTargetMono service = getService();

        service.value = "1";
        assertNotNull(service);

        // when
        final String initial = service.getValue("1", BigDecimal.ZERO).block(Duration.ofMinutes(1));
        final String cached = service.putValue(BigDecimal.ZERO, "5", "1").block(Duration.ofMinutes(1));
        assertEquals(initial, cached);
        service.value = "2";
        service.evictValue("1", BigDecimal.ZERO).block(Duration.ofMinutes(1));

        // then
        final String fromCache = service.getValue("1", BigDecimal.ZERO).block(Duration.ofMinutes(1));
        assertNotEquals(cached, fromCache);
    }

    @Test
    void getFromCacheWhenCacheInvalidateAll() {
        // given
        final CacheableTargetMono service = getService();

        service.value = "1";
        assertNotNull(service);

        // when
        final String initial = service.getValue("1", BigDecimal.ZERO).block(Duration.ofMinutes(1));
        final String cached = service.putValue(BigDecimal.ZERO, "5", "1").block(Duration.ofMinutes(1));
        assertEquals(initial, cached);
        service.value = "2";
        service.evictAll().block(Duration.ofMinutes(1));

        // then
        final String fromCache = service.getValue("1", BigDecimal.ZERO).block(Duration.ofMinutes(1));
        assertNotEquals(cached, fromCache);
    }
}
