package ru.tinkoff.kora.cache.annotation.processor;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import ru.tinkoff.kora.annotation.processor.common.TestUtils;
import ru.tinkoff.kora.aop.annotation.processor.AopAnnotationProcessor;
import ru.tinkoff.kora.cache.CacheKey;
import ru.tinkoff.kora.cache.annotation.processor.testcache.DummyCache2;
import ru.tinkoff.kora.cache.annotation.processor.testdata.reactive.mono.CacheableMono;
import ru.tinkoff.kora.cache.caffeine.CaffeineCacheConfig;
import ru.tinkoff.kora.cache.caffeine.CaffeineCacheModule;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MonoCacheAopTests extends Assertions implements CaffeineCacheModule {

    private static final String CACHED_IMPL = "ru.tinkoff.kora.cache.annotation.processor.testcache.$DummyCache2Impl";
    private static final String CACHED_SERVICE = "ru.tinkoff.kora.cache.annotation.processor.testdata.reactive.mono.$CacheableMono__AopProxy";

    private DummyCache2 cache = null;
    private CacheableMono service = null;

    private CacheableMono getService() {
        if (service != null) {
            return service;
        }

        try {
            var classLoader = TestUtils.annotationProcess(List.of(DummyCache2.class, CacheableMono.class),
                new AopAnnotationProcessor(), new CacheAnnotationProcessor());

            var cacheClass = classLoader.loadClass(CACHED_IMPL);
            if (cacheClass == null) {
                throw new IllegalArgumentException("Expected class not found: " + CACHED_SERVICE);
            }

            final Constructor<?> cacheConstructor = cacheClass.getDeclaredConstructors()[0];
            cacheConstructor.setAccessible(true);
            cache = (DummyCache2) cacheConstructor.newInstance(CacheRunner.getConfig(),
                caffeineCacheFactory(null), caffeineCacheTelemetry(null, null));

            var serviceClass = classLoader.loadClass(CACHED_SERVICE);
            if (serviceClass == null) {
                throw new IllegalArgumentException("Expected class not found: " + CACHED_SERVICE);
            }

            final Constructor<?> serviceConstructor = serviceClass.getDeclaredConstructors()[0];
            serviceConstructor.setAccessible(true);
            service = (CacheableMono) serviceConstructor.newInstance(cache);
            return service;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @BeforeEach
    void cleanup() {
        if (cache != null) {
            cache.invalidateAll();
        }
    }

    @Test
    void getFromCacheWhenWasCacheEmpty() {
        // given
        var service = getService();
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
        var service = getService();
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
        var service = getService();
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
        var service = getService();
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
        var service = getService();
        service.value = "1";
        assertNotNull(service);

        // when
        final String initial = service.getValue("1", BigDecimal.ZERO).block(Duration.ofMinutes(1));
        final String cached = service.putValue(BigDecimal.ZERO, "5", "1").block(Duration.ofMinutes(1));
        assertEquals(initial, cached);

        final String cached2 = service.putValue(BigDecimal.ZERO, "5", "2").block(Duration.ofMinutes(1));
        assertEquals(initial, cached2);

        service.value = "2";
        service.evictValue("1", BigDecimal.ZERO).block(Duration.ofMinutes(1));

        // then
        assertNull(cache.get(CacheKey.of("1", BigDecimal.ZERO)));
        assertEquals(cached2, cache.get(CacheKey.of("2", BigDecimal.ZERO)));

        final String fromCache = service.getValue("1", BigDecimal.ZERO).block(Duration.ofMinutes(1));
        assertNotEquals(cached, fromCache);
    }

    @Test
    void getFromCacheWhenCacheInvalidateAll() {
        // given
        var service = getService();
        service.value = "1";
        assertNotNull(service);

        // when
        final String initial = service.getValue("1", BigDecimal.ZERO).block(Duration.ofMinutes(1));
        final String cached = service.putValue(BigDecimal.ZERO, "5", "1").block(Duration.ofMinutes(1));
        assertEquals(initial, cached);

        final String cached2 = service.putValue(BigDecimal.ZERO, "5", "2").block(Duration.ofMinutes(1));
        assertEquals(initial, cached2);

        service.value = "2";
        service.evictAll().block(Duration.ofMinutes(1));

        // then
        assertNull(cache.get(CacheKey.of("1", BigDecimal.ZERO)));
        assertNull(cache.get(CacheKey.of("2", BigDecimal.ZERO)));

        final String fromCache = service.getValue("1", BigDecimal.ZERO).block(Duration.ofMinutes(1));
        assertNotEquals(cached, fromCache);
    }
}
