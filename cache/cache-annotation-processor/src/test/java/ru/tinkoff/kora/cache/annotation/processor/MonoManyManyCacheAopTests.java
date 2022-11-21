package ru.tinkoff.kora.cache.annotation.processor;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import ru.tinkoff.kora.annotation.processor.common.TestUtils;
import ru.tinkoff.kora.aop.annotation.processor.AopAnnotationProcessor;
import ru.tinkoff.kora.cache.CacheKey;
import ru.tinkoff.kora.cache.annotation.processor.testcache.DummyCache;
import ru.tinkoff.kora.cache.annotation.processor.testcache.DummyCacheManager;
import ru.tinkoff.kora.cache.annotation.processor.testdata.reactive.mono.CacheableTargetMonoManyMany;

import javax.annotation.Nonnull;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

@SuppressWarnings({"rawtypes", "unchecked"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MonoManyManyCacheAopTests extends Assertions {

    public static final String CACHED_SERVICE = "ru.tinkoff.kora.cache.annotation.processor.testdata.reactive.mono.$CacheableTargetMonoManyMany__AopProxy";

    private final DummyCacheManager<?, ?> cacheManager = new DummyCacheManager<>();
    private CacheableTargetMonoManyMany service = null;

    private CacheableTargetMonoManyMany getService(DummyCacheManager<?, ?> manager) {
        if (service != null) {
            return service;
        }

        try {
            var classLoader = TestUtils.annotationProcess(CacheableTargetMonoManyMany.class, new AopAnnotationProcessor(), new CacheKeyAnnotationProcessor());
            var serviceClass = classLoader.loadClass(CACHED_SERVICE);
            if (serviceClass == null) {
                throw new IllegalArgumentException("Expected class not found: " + CACHED_SERVICE);
            }
            service = (CacheableTargetMonoManyMany) serviceClass.getConstructors()[0].newInstance(manager);
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
        final CacheableTargetMonoManyMany service = getService(cacheManager);
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
    void getFromCacheLevel3AndThenSaveCacheLevel1AndLevel2() {
        // given
        final CacheableTargetMonoManyMany service = getService(cacheManager);
        final DummyCache cache1 = cacheManager.getCache("mono_cache");
        final DummyCache cache2 = cacheManager.getCache("mono_cache_2");
        final DummyCache cache3 = cacheManager.getCache("mono_cache_3");
        service.value = "1";
        assertNotNull(service);
        assertTrue(cache1.isEmpty());
        assertTrue(cache2.isEmpty());
        assertTrue(cache3.isEmpty());

        var cachedValue = "LEVEL_3";
        var cachedKey = new CacheKey() {
            @Nonnull
            @Override
            public List<Object> values() {
                return Arrays.asList("1", BigDecimal.ZERO);
            }

            @Override
            public String toString() {
                return "1" + "-" + BigDecimal.ZERO;
            }
        };

        cache3.put(cachedKey, cachedValue);
        assertFalse(cache3.isEmpty());

        // when
        final String valueFromLevel3 = service.getValue("1", BigDecimal.ZERO).block(Duration.ofMinutes(1));
        service.value = "2";

        // then
        final String valueFromLevel1 = service.getValue("1", BigDecimal.ZERO).block(Duration.ofMinutes(1));
        assertEquals(valueFromLevel3, valueFromLevel1);
        assertEquals(cachedValue, valueFromLevel1);
        assertFalse(cache1.isEmpty());
        assertFalse(cache2.isEmpty());
        assertFalse(cache3.isEmpty());
    }

    @Test
    void getFromCacheLevel2AndThenSaveCacheLevel1() {
        // given
        final CacheableTargetMonoManyMany service = getService(cacheManager);
        final DummyCache cache1 = cacheManager.getCache("mono_cache");
        final DummyCache cache2 = cacheManager.getCache("mono_cache_2");
        final DummyCache cache3 = cacheManager.getCache("mono_cache_3");
        service.value = "1";
        assertNotNull(service);
        assertTrue(cache1.isEmpty());
        assertTrue(cache2.isEmpty());
        assertTrue(cache3.isEmpty());

        var cachedValue = "LEVEL_2";
        var cachedKey = new CacheKey() {
            @Nonnull
            @Override
            public List<Object> values() {
                return Arrays.asList("1", BigDecimal.ZERO);
            }

            @Override
            public String toString() {
                return "1" + "-" + BigDecimal.ZERO;
            }
        };

        cache2.put(cachedKey, cachedValue);
        assertFalse(cache2.isEmpty());

        // when
        final String valueFromLevel2 = service.getValue("1", BigDecimal.ZERO).block(Duration.ofMinutes(1));
        service.value = "2";

        // then
        final String valueFromLevel1 = service.getValue("1", BigDecimal.ZERO).block(Duration.ofMinutes(1));
        assertEquals(valueFromLevel2, valueFromLevel1);
        assertEquals(cachedValue, valueFromLevel1);
        assertFalse(cache1.isEmpty());
        assertFalse(cache2.isEmpty());
        assertTrue(cache3.isEmpty());
    }

    @Test
    void getFromCacheWhenCacheFilled() {
        // given
        final CacheableTargetMonoManyMany service = getService(cacheManager);
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
        final CacheableTargetMonoManyMany service = getService(cacheManager);
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
        final CacheableTargetMonoManyMany service = getService(cacheManager);
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
        final CacheableTargetMonoManyMany service = getService(cacheManager);
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
        final CacheableTargetMonoManyMany service = getService(cacheManager);
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
