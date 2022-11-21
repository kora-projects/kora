package ru.tinkoff.kora.cache.annotation.processor;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import ru.tinkoff.kora.annotation.processor.common.TestUtils;
import ru.tinkoff.kora.aop.annotation.processor.AopAnnotationProcessor;
import ru.tinkoff.kora.cache.annotation.processor.testcache.DummyCacheManager;
import ru.tinkoff.kora.cache.annotation.processor.testdata.sync.CacheableTargetSync;

import java.math.BigDecimal;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SyncCacheAopTests extends Assertions {

    private static final String CACHED_SERVICE = "ru.tinkoff.kora.cache.annotation.processor.testdata.sync.$CacheableTargetSync__AopProxy";

    private final DummyCacheManager<?, ?> cacheManager = new DummyCacheManager<>();
    private CacheableTargetSync service = null;

    private CacheableTargetSync getService(DummyCacheManager<?, ?> manager) {
        if (service != null) {
            return service;
        }

        try {
            var classLoader = TestUtils.annotationProcess(CacheableTargetSync.class, new AopAnnotationProcessor(), new CacheKeyAnnotationProcessor());
            var serviceClass = classLoader.loadClass(CACHED_SERVICE);
            if (serviceClass == null) {
                throw new IllegalArgumentException("Expected class not found: " + CACHED_SERVICE);
            }
            service = (CacheableTargetSync) serviceClass.getConstructors()[0].newInstance(manager);
            return service;
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    @BeforeEach
    void reset() {
        cacheManager.reset();
    }

    @Test
    void getFromCacheWhenWasCacheEmpty() {
        // given
        final CacheableTargetSync service = getService(cacheManager);
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
        final CacheableTargetSync service = getService(cacheManager);
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
        final CacheableTargetSync service = getService(cacheManager);
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
        final CacheableTargetSync service = getService(cacheManager);
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
        final CacheableTargetSync service = getService(cacheManager);
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
        final CacheableTargetSync service = getService(cacheManager);
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
