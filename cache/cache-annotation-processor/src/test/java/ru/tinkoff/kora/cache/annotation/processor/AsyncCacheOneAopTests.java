package ru.tinkoff.kora.cache.annotation.processor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import ru.tinkoff.kora.annotation.processor.common.TestUtils;
import ru.tinkoff.kora.aop.annotation.processor.AopAnnotationProcessor;
import ru.tinkoff.kora.cache.annotation.processor.testcache.DummyCache11;
import ru.tinkoff.kora.cache.annotation.processor.testdata.async.CacheableAsyncOne;
import ru.tinkoff.kora.cache.caffeine.CaffeineCacheModule;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AsyncCacheOneAopTests implements CaffeineCacheModule {

    private static final String CACHED_IMPL = "ru.tinkoff.kora.cache.annotation.processor.testcache.$DummyCache11Impl";
    private static final String CACHED_SERVICE = "ru.tinkoff.kora.cache.annotation.processor.testdata.async.$CacheableAsyncOne__AopProxy";

    private DummyCache11 cache = null;
    private CacheableAsyncOne service = null;

    private CacheableAsyncOne getService() {
        if (service != null) {
            return service;
        }

        try {
            var classLoader = TestUtils.annotationProcess(List.of(DummyCache11.class, CacheableAsyncOne.class),
                new AopAnnotationProcessor(), new CacheAnnotationProcessor());

            var cacheClass = classLoader.loadClass(CACHED_IMPL);
            if (cacheClass == null) {
                throw new IllegalArgumentException("Expected class not found: " + CACHED_SERVICE);
            }

            final Constructor<?> cacheConstructor = cacheClass.getDeclaredConstructors()[0];
            cacheConstructor.setAccessible(true);
            cache = (DummyCache11) cacheConstructor.newInstance(CacheRunner.getCaffeineConfig(),
                caffeineCacheFactory(null), caffeineCacheTelemetry(null, null));

            var serviceClass = classLoader.loadClass(CACHED_SERVICE);
            if (serviceClass == null) {
                throw new IllegalArgumentException("Expected class not found: " + CACHED_SERVICE);
            }

            final Constructor<?> serviceConstructor = serviceClass.getDeclaredConstructors()[0];
            serviceConstructor.setAccessible(true);
            service = (CacheableAsyncOne) serviceConstructor.newInstance(cache);
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
        final String notCached = service.getValue("1").toCompletableFuture().join();
        service.value = "2";

        // then
        final String fromCache = service.getValue("1").toCompletableFuture().join();
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
        final String initial = service.getValue("1").toCompletableFuture().join();
        final String cached = service.putValue(BigDecimal.ZERO, "5", "1").toCompletableFuture().join();
        assertEquals(initial, cached);
        service.value = "2";

        // then
        final String fromCache = service.getValue("1").toCompletableFuture().join();
        assertEquals(cached, fromCache);
    }

    @Test
    void getFromCacheWrongKeyWhenCacheFilled() {
        // given
        var service = getService();
        service.value = "1";
        assertNotNull(service);

        // when
        final String initial = service.getValue("1").toCompletableFuture().join();
        final String cached = service.putValue(BigDecimal.ZERO, "5", "1").toCompletableFuture().join();
        assertEquals(initial, cached);
        service.value = "2";

        // then
        final String fromCache = service.getValue("2").toCompletableFuture().join();
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
        final String cached = service.putValue(BigDecimal.ZERO, "5", "1").toCompletableFuture().join();
        service.value = "2";
        final String initial = service.getValue("2").toCompletableFuture().join();
        assertNotEquals(cached, initial);

        // then
        final String fromCache = service.getValue("2").toCompletableFuture().join();
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
        final String initial = service.getValue("1").toCompletableFuture().join();
        final String cached = service.putValue(BigDecimal.ZERO, "5", "1").toCompletableFuture().join();
        assertEquals(initial, cached);
        service.value = "2";
        service.evictValue("1").toCompletableFuture().join();

        // then
        final String fromCache = service.getValue("1").toCompletableFuture().join();
        assertNotEquals(cached, fromCache);
    }

    @Test
    void getFromCacheWhenCacheInvalidateAll() {
        // given
        var service = getService();
        service.value = "1";
        assertNotNull(service);

        // when
        final String initial = service.getValue("1").toCompletableFuture().join();
        final String cached = service.putValue(BigDecimal.ZERO, "5", "1").toCompletableFuture().join();
        assertEquals(initial, cached);
        service.value = "2";
        service.evictAll().toCompletableFuture().join();

        // then
        final String fromCache = service.getValue("1").toCompletableFuture().join();
        assertNotEquals(cached, fromCache);
    }
}
