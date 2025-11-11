package ru.tinkoff.kora.cache.annotation.processor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import ru.tinkoff.kora.annotation.processor.common.TestUtils;
import ru.tinkoff.kora.aop.annotation.processor.AopAnnotationProcessor;
import ru.tinkoff.kora.cache.annotation.processor.testcache.DummyCache11;
import ru.tinkoff.kora.cache.annotation.processor.testcache.DummyCache12;
import ru.tinkoff.kora.cache.annotation.processor.testdata.sync.CacheableSyncOneMany;
import ru.tinkoff.kora.cache.caffeine.CaffeineCacheModule;
import ru.tinkoff.kora.cache.redis.RedisCacheModule;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SyncCacheOneManyAopTests implements CaffeineCacheModule, RedisCacheModule {

    private static final String CACHED_IMPL_1 = "ru.tinkoff.kora.cache.annotation.processor.testcache.$DummyCache11Impl";
    private static final String CACHED_IMPL_2 = "ru.tinkoff.kora.cache.annotation.processor.testcache.$DummyCache12Impl";
    private static final String CACHED_SERVICE = "ru.tinkoff.kora.cache.annotation.processor.testdata.sync.$CacheableSyncOneMany__AopProxy";

    private DummyCache11 cache1 = null;
    private DummyCache12 cache2 = null;
    private CacheableSyncOneMany service = null;

    private CacheableSyncOneMany getService() {
        if (service != null) {
            return service;
        }

        try {
            var classLoader = TestUtils.annotationProcess(List.of(DummyCache11.class, DummyCache12.class, CacheableSyncOneMany.class),
                new AopAnnotationProcessor(), new CacheAnnotationProcessor());

            var cacheClass1 = classLoader.loadClass(CACHED_IMPL_1);
            if (cacheClass1 == null) {
                throw new IllegalArgumentException("Expected class not found: " + CACHED_IMPL_1);
            }

            final Constructor<?> cacheConstructor1 = cacheClass1.getDeclaredConstructors()[0];
            cacheConstructor1.setAccessible(true);
            cache1 = (DummyCache11) cacheConstructor1.newInstance(CacheRunner.getCaffeineConfig(),
                caffeineCacheFactory(null));

            var cacheClass2 = classLoader.loadClass(CACHED_IMPL_2);
            if (cacheClass2 == null) {
                throw new IllegalArgumentException("Expected class not found: " + CACHED_IMPL_2);
            }

            final Constructor<?> cacheConstructor2 = cacheClass2.getDeclaredConstructors()[0];
            cacheConstructor2.setAccessible(true);
            final Map<ByteBuffer, ByteBuffer> cache = new HashMap<>();
            cache2 = (DummyCache12) cacheConstructor2.newInstance(CacheRunner.getRedisConfig(),
                CacheRunner.lettuceClient(cache), redisCacheTelemetry(null, null),
                stringRedisKeyMapper(), stringRedisValueMapper());

            var serviceClass = classLoader.loadClass(CACHED_SERVICE);
            if (serviceClass == null) {
                throw new IllegalArgumentException("Expected class not found: " + CACHED_SERVICE);
            }

            final Constructor<?> serviceConstructor = serviceClass.getDeclaredConstructors()[0];
            serviceConstructor.setAccessible(true);
            service = (CacheableSyncOneMany) serviceConstructor.newInstance(cache1, cache2);
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
        var service = getService();
        service.value = "1";
        assertNotNull(service);

        // when
        final String notCached = service.getValue("1");
        service.value = "2";

        // then
        final String fromCache = service.getValue("1");
        assertEquals(notCached, fromCache);
        assertNotEquals("2", fromCache);
    }


    @Test
    void getFromCacheLevel2AndThenSaveCacheLevel1() {
        // given
        var service = getService();
        service.value = "1";
        assertNotNull(service);

        var cachedValue = "LEVEL_2";
        cache2.put("1", cachedValue);

        // when
        final String valueFromLevel2 = service.getValue("1");
        service.value = "2";

        // then
        final String valueFromLevel1 = service.getValue("1");
        assertEquals(valueFromLevel2, valueFromLevel1);
        assertEquals(cachedValue, valueFromLevel1);
    }

    @Test
    void getFromCacheWhenCacheFilled() {
        // given
        var service = getService();
        service.value = "1";
        assertNotNull(service);

        // when
        final String initial = service.getValue("1");
        final String cached = service.putValue(BigDecimal.ZERO, "5", "1");
        assertEquals(initial, cached);
        service.value = "2";

        // then
        final String fromCache = service.getValue("1");
        assertEquals(cached, fromCache);
    }

    @Test
    void getFromCacheWrongKeyWhenCacheFilled() {
        // given
        var service = getService();
        service.value = "1";
        assertNotNull(service);

        // when
        final String initial = service.getValue("1");
        final String cached = service.putValue(BigDecimal.ZERO, "5", "1");
        assertEquals(initial, cached);
        service.value = "2";

        // then
        final String fromCache = service.getValue("2");
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
        final String cached = service.putValue(BigDecimal.ZERO, "5", "1");
        service.value = "2";
        final String initial = service.getValue("2");
        assertNotEquals(cached, initial);

        // then
        final String fromCache = service.getValue("2");
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
        final String initial = service.getValue("1");
        final String cached = service.putValue(BigDecimal.ZERO, "5", "1");
        assertEquals(initial, cached);
        service.value = "2";
        service.evictValue("1");

        // then
        final String fromCache = service.getValue("1");
        assertNotEquals(cached, fromCache);
    }

    @Test
    void getFromCacheWhenCacheInvalidateAll() {
        // given
        var service = getService();
        service.value = "1";
        assertNotNull(service);

        // when
        final String initial = service.getValue("1");
        final String cached = service.putValue(BigDecimal.ZERO, "5", "1");
        assertEquals(initial, cached);
        service.value = "2";
        service.evictAll();

        // then
        final String fromCache = service.getValue("1");
        assertNotEquals(cached, fromCache);
    }
}
