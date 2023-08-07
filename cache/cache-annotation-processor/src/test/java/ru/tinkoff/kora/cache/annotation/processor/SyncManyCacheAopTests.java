package ru.tinkoff.kora.cache.annotation.processor;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import ru.tinkoff.kora.annotation.processor.common.TestUtils;
import ru.tinkoff.kora.aop.annotation.processor.AopAnnotationProcessor;
import ru.tinkoff.kora.cache.CacheKey;
import ru.tinkoff.kora.cache.annotation.processor.testcache.DummyCache2;
import ru.tinkoff.kora.cache.annotation.processor.testcache.DummyCache22;
import ru.tinkoff.kora.cache.annotation.processor.testdata.sync.CacheableSyncMany;
import ru.tinkoff.kora.cache.caffeine.CaffeineCacheConfig;
import ru.tinkoff.kora.cache.caffeine.CaffeineCacheModule;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.util.List;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SyncManyCacheAopTests extends Assertions implements CaffeineCacheModule {

    private static final String CACHED_IMPL_1 = "ru.tinkoff.kora.cache.annotation.processor.testcache.$DummyCache2Impl";
    private static final String CACHED_IMPL_2 = "ru.tinkoff.kora.cache.annotation.processor.testcache.$DummyCache22Impl";
    private static final String CACHED_SERVICE = "ru.tinkoff.kora.cache.annotation.processor.testdata.sync.$CacheableSyncMany__AopProxy";

    private DummyCache2 cache1 = null;
    private DummyCache22 cache2 = null;
    private CacheableSyncMany service = null;

    private CacheableSyncMany getService() {
        if (service != null) {
            return service;
        }

        try {
            var classLoader = TestUtils.annotationProcess(List.of(DummyCache2.class, DummyCache22.class, CacheableSyncMany.class),
                new AopAnnotationProcessor(), new CacheAnnotationProcessor());

            var cacheClass1 = classLoader.loadClass(CACHED_IMPL_1);
            if (cacheClass1 == null) {
                throw new IllegalArgumentException("Expected class not found: " + CACHED_IMPL_1);
            }

            final Constructor<?> cacheConstructor1 = cacheClass1.getDeclaredConstructors()[0];
            cacheConstructor1.setAccessible(true);
            cache1 = (DummyCache2) cacheConstructor1.newInstance(CacheRunner.getConfig(),
                caffeineCacheFactory(null), caffeineCacheTelemetry(null, null));

            var cacheClass2 = classLoader.loadClass(CACHED_IMPL_2);
            if (cacheClass2 == null) {
                throw new IllegalArgumentException("Expected class not found: " + CACHED_IMPL_2);
            }

            final Constructor<?> cacheConstructor2 = cacheClass2.getDeclaredConstructors()[0];
            cacheConstructor2.setAccessible(true);
            cache2 = (DummyCache22) cacheConstructor2.newInstance(CacheRunner.getConfig(),
                caffeineCacheFactory(null), caffeineCacheTelemetry(null, null));

            var serviceClass = classLoader.loadClass(CACHED_SERVICE);
            if (serviceClass == null) {
                throw new IllegalArgumentException("Expected class not found: " + CACHED_SERVICE);
            }

            final Constructor<?> serviceConstructor = serviceClass.getDeclaredConstructors()[0];
            serviceConstructor.setAccessible(true);
            service = (CacheableSyncMany) serviceConstructor.newInstance(cache1, cache2);
            return service;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @BeforeEach
    void cleanup() {
        if (cache1 != null && cache2 != null) {
            cache1.invalidateAll();
            cache2.invalidateAll();
        }
    }

    @Test
    void getFromCacheWhenWasCacheEmpty() {
        // given
        final CacheableSyncMany service = getService();
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
    void getFromCacheLevel2AndThenSaveCacheLevel1() {
        // given
        final CacheableSyncMany service = getService();
        service.value = "1";
        assertNotNull(service);

        var cachedValue = "LEVEL_2";
        cache2.put(CacheKey.of("1", BigDecimal.ZERO), cachedValue);

        // when
        final String valueFromLevel2 = service.getValue("1", BigDecimal.ZERO);
        service.value = "2";

        // then
        final String valueFromLevel1 = service.getValue("1", BigDecimal.ZERO);
        assertEquals(valueFromLevel2, valueFromLevel1);
        assertEquals(cachedValue, valueFromLevel1);
    }

    @Test
    void getFromCacheWhenCacheFilled() {
        // given
        final CacheableSyncMany service = getService();
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
        final CacheableSyncMany service = getService();
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
        final CacheableSyncMany service = getService();
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
        final CacheableSyncMany service = getService();
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
        final CacheableSyncMany service = getService();
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
