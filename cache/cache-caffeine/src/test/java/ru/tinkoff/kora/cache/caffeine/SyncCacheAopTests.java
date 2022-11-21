package ru.tinkoff.kora.cache.caffeine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import ru.tinkoff.kora.cache.caffeine.testdata.CacheableMockLifecycle;
import ru.tinkoff.kora.cache.caffeine.testdata.CacheableTargetSync;

import java.math.BigDecimal;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SyncCacheAopTests extends CacheRunner {

    private CacheableTargetSync service = null;

    private CacheableTargetSync getService() {
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
            .map(a -> ((CacheableMockLifecycle) a).sync())
            .findFirst().orElseThrow();
        return service;
    }

    @BeforeEach
    void reset() {
        if (service != null) {
            service.evictAll();
        }
    }

    @Test
    void getFromCacheWhenWasCacheEmpty() {
        // given
        final CacheableTargetSync service = getService();

        service.value = "1";
        assertNotNull(service);

        // when
        final String notCached = service.getValue("1", BigDecimal.ZERO);
        service.value = "2";

        // then
        final String fromCache = service.getValue("1", BigDecimal.ZERO);
        assertEquals(notCached, fromCache);
        assertNotEquals("2", fromCache);
    }

    @Test
    void getFromCacheWhenCacheFilled() {
        // given
        final CacheableTargetSync service = getService();

        service.value = "1";
        assertNotNull(service);

        // when
        final String initial = service.getValue("1", BigDecimal.ZERO);
        final String cached = service.putValue(BigDecimal.ZERO, "5", "1");
        assertEquals(initial, cached);
        service.value = "2";

        // then
        final String fromCache = service.getValue("1", BigDecimal.ZERO);
        assertEquals(cached, fromCache);
    }

    @Test
    void getFromCacheWrongKeyWhenCacheFilled() {
        // given
        final CacheableTargetSync service = getService();

        service.value = "1";
        assertNotNull(service);

        // when
        final String initial = service.getValue("1", BigDecimal.ZERO);
        final String cached = service.putValue(BigDecimal.ZERO, "5", "1");
        assertEquals(initial, cached);
        service.value = "2";

        // then
        final String fromCache = service.getValue("2", BigDecimal.ZERO);
        assertNotEquals(cached, fromCache);
        assertEquals(service.value, fromCache);
    }

    @Test
    void getFromCacheWhenCacheFilledOtherKey() {
        // given
        final CacheableTargetSync service = getService();

        service.value = "1";
        assertNotNull(service);

        // when
        final String cached = service.putValue(BigDecimal.ZERO, "5", "1");
        service.value = "2";
        final String initial = service.getValue("2", BigDecimal.ZERO);
        assertNotEquals(cached, initial);

        // then
        final String fromCache = service.getValue("2", BigDecimal.ZERO);
        assertNotEquals(cached, fromCache);
        assertEquals(initial, fromCache);
    }

    @Test
    void getFromCacheWhenCacheInvalidate() {
        // given
        final CacheableTargetSync service = getService();

        service.value = "1";
        assertNotNull(service);

        // when
        final String initial = service.getValue("1", BigDecimal.ZERO);
        final String cached = service.putValue(BigDecimal.ZERO, "5", "1");
        assertEquals(initial, cached);
        service.value = "2";
        service.evictValue("1", BigDecimal.ZERO);

        // then
        final String fromCache = service.getValue("1", BigDecimal.ZERO);
        assertNotEquals(cached, fromCache);
    }

    @Test
    void getFromCacheWhenCacheInvalidateAll() {
        // given
        final CacheableTargetSync service = getService();

        service.value = "1";
        assertNotNull(service);

        // when
        final String initial = service.getValue("1", BigDecimal.ZERO);
        final String cached = service.putValue(BigDecimal.ZERO, "5", "1");
        assertEquals(initial, cached);
        service.value = "2";
        service.evictAll();

        // then
        final String fromCache = service.getValue("1", BigDecimal.ZERO);
        assertNotEquals(cached, fromCache);
    }
}
