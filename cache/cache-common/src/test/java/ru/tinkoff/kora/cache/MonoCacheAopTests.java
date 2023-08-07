package ru.tinkoff.kora.cache;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import ru.tinkoff.kora.cache.testcache.DummyCache;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MonoCacheAopTests extends Assertions {

    private final DummyCache cache1 = new DummyCache("cache1");

    @BeforeEach
    void cleanup() {
        cache1.invalidateAll();
    }

    @Test
    void getWhenCacheEmpty() {
        // given
        final DummyCache cache2 = new DummyCache("cache2");
        final Cache<String, String> facade = CacheBuilder.builder(cache1)
            .addCache(cache2)
            .build();

        // then
        assertNull(facade.getAsync("key1").block());
    }

    @Test
    void getForFacade1() {
        // given
        final DummyCache cache2 = new DummyCache("cache2");
        final Cache<String, String> facade = CacheBuilder.builder(cache1)
            .addCache(cache2)
            .build();

        // when
        final String result = "value1";
        cache1.putAsync("key1", result).block();

        // then
        assertEquals(result, facade.getAsync("key1").block());
    }

    @Test
    void getForFacade2() {
        // given
        final DummyCache cache2 = new DummyCache("cache2");
        final Cache<String, String> facade = CacheBuilder.builder(cache1)
            .addCache(cache2)
            .build();

        // when
        final String result = "value1";
        cache2.putAsync("key1", result).block();

        // then
        assertEquals(result, facade.getAsync("key1").block());
    }

    @Test
    void putForFacade12() {
        // given
        final DummyCache cache2 = new DummyCache("cache2");
        final Cache<String, String> facade = CacheBuilder.builder(cache1)
            .addCache(cache2)
            .build();

        // when
        final String result = "value1";
        facade.putAsync("key1", result).block();

        // then
        assertEquals(result, facade.getAsync("key1").block());
        assertEquals(result, cache1.getAsync("key1").block());
        assertEquals(result, cache2.getAsync("key1").block());
    }

    @Test
    void invalidateCacheForFacade() {
        // given
        final DummyCache cache2 = new DummyCache("cache2");
        final Cache<String, String> facade = CacheBuilder.builder(cache1)
            .addCache(cache2)
            .build();

        final String result = "value1";
        facade.putAsync("key1", result).block();
        assertEquals(result, facade.getAsync("key1").block());
        assertEquals(result, cache1.getAsync("key1").block());
        assertEquals(result, cache2.getAsync("key1").block());

        // when
        facade.invalidateAsync("key1").block();

        // then
        assertNull(facade.getAsync("key1").block());
        assertNull(cache1.getAsync("key1").block());
        assertNull(cache2.getAsync("key1").block());
    }

    @Test
    void invalidateAllCacheForFacade() {
        // given
        final DummyCache cache2 = new DummyCache("cache2");
        final Cache<String, String> facade = CacheBuilder.builder(cache1)
            .addCache(cache2)
            .build();

        final String result = "value1";
        facade.putAsync("key1", result).block();
        assertEquals(result, facade.getAsync("key1").block());
        assertEquals(result, cache1.getAsync("key1").block());
        assertEquals(result, cache2.getAsync("key1").block());

        // when
        facade.invalidateAllAsync().block();

        // then
        assertNull(facade.getAsync("key1").block());
        assertNull(cache1.getAsync("key1").block());
        assertNull(cache2.getAsync("key1").block());
    }
}
