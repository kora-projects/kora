package ru.tinkoff.kora.cache;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import ru.tinkoff.kora.cache.testcache.DummyCache;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SyncCacheAopTests extends Assertions {

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
        assertNull(facade.get("key1"));
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
        cache1.put("key1", result);

        // then
        assertEquals(result, facade.get("key1"));
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
        cache2.put("key1", result);

        // then
        assertEquals(result, facade.get("key1"));
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
        facade.put("key1", result);

        // then
        assertEquals(result, facade.get("key1"));
        assertEquals(result, cache1.get("key1"));
        assertEquals(result, cache2.get("key1"));
    }

    @Test
    void invalidateCacheForFacade() {
        // given
        final DummyCache cache2 = new DummyCache("cache2");
        final Cache<String, String> facade = CacheBuilder.builder(cache1)
            .addCache(cache2)
            .build();

        final String result = "value1";
        facade.put("key1", result);
        assertEquals(result, facade.get("key1"));
        assertEquals(result, cache1.get("key1"));
        assertEquals(result, cache2.get("key1"));

        // when
        facade.invalidate("key1");

        // then
        assertNull(facade.get("key1"));
        assertNull(cache1.get("key1"));
        assertNull(cache2.get("key1"));
    }

    @Test
    void invalidateAllCacheForFacade() {
        // given
        final DummyCache cache2 = new DummyCache("cache2");
        final Cache<String, String> facade = CacheBuilder.builder(cache1)
            .addCache(cache2)
            .build();

        final String result = "value1";
        facade.put("key1", result);
        assertEquals(result, facade.get("key1"));
        assertEquals(result, cache1.get("key1"));
        assertEquals(result, cache2.get("key1"));

        // when
        facade.invalidateAll();

        // then
        assertNull(facade.get("key1"));
        assertNull(cache1.get("key1"));
        assertNull(cache2.get("key1"));
    }
}
