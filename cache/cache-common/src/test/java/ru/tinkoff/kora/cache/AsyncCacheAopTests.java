package ru.tinkoff.kora.cache;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import ru.tinkoff.kora.cache.testcache.DummyCache;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AsyncCacheAopTests extends Assertions {

    private final DummyCache cache1 = new DummyCache("cache1");

    @BeforeEach
    void cleanup() {
        cache1.invalidateAll();
    }

    @Test
    void getWhenCacheEmpty() {
        // given
        final DummyCache cache2 = new DummyCache("cache2");
        final AsyncCache<String, String> facade = AsyncCache.builder(cache1)
            .addCache(cache2)
            .build();

        // then
        assertNull(facade.getAsync("key1").toCompletableFuture().join());
    }

    @Test
    void getForFacade1() {
        // given
        final DummyCache cache2 = new DummyCache("cache2");
        final AsyncCache<String, String> facade = AsyncCache.builder(cache1)
            .addCache(cache2)
            .build();

        // when
        final String result = "value1";
        cache1.putAsync("key1", result).toCompletableFuture().join();

        // then
        assertEquals(result, facade.getAsync("key1").toCompletableFuture().join());
    }

    @Test
    void getForFacade2() {
        // given
        final DummyCache cache2 = new DummyCache("cache2");
        final AsyncCache<String, String> facade = AsyncCache.builder(cache1)
            .addCache(cache2)
            .build();

        // when
        final String result = "value1";
        cache2.putAsync("key1", result).toCompletableFuture().join();

        // then
        assertEquals(result, facade.getAsync("key1").toCompletableFuture().join());
    }

    @Test
    void putForFacade12() {
        // given
        final DummyCache cache2 = new DummyCache("cache2");
        final AsyncCache<String, String> facade = AsyncCache.builder(cache1)
            .addCache(cache2)
            .build();

        // when
        final String result = "value1";
        facade.putAsync("key1", result).toCompletableFuture().join();

        // then
        assertEquals(result, facade.getAsync("key1").toCompletableFuture().join());
        assertEquals(result, cache1.getAsync("key1").toCompletableFuture().join());
        assertEquals(result, cache2.getAsync("key1").toCompletableFuture().join());
    }

    @Test
    void invalidateCacheForFacade() {
        // given
        final DummyCache cache2 = new DummyCache("cache2");
        final AsyncCache<String, String> facade = AsyncCache.builder(cache1)
            .addCache(cache2)
            .build();

        final String result = "value1";
        facade.putAsync("key1", result).toCompletableFuture().join();
        assertEquals(result, facade.getAsync("key1").toCompletableFuture().join());
        assertEquals(result, cache1.getAsync("key1").toCompletableFuture().join());
        assertEquals(result, cache2.getAsync("key1").toCompletableFuture().join());

        // when
        facade.invalidateAsync("key1").toCompletableFuture().join();

        // then
        assertNull(facade.getAsync("key1").toCompletableFuture().join());
        assertNull(cache1.getAsync("key1").toCompletableFuture().join());
        assertNull(cache2.getAsync("key1").toCompletableFuture().join());
    }

    @Test
    void invalidateAllCacheForFacade() {
        // given
        final DummyCache cache2 = new DummyCache("cache2");
        final AsyncCache<String, String> facade = AsyncCache.builder(cache1)
            .addCache(cache2)
            .build();

        final String result = "value1";
        facade.putAsync("key1", result).toCompletableFuture().join();
        assertEquals(result, facade.getAsync("key1").toCompletableFuture().join());
        assertEquals(result, cache1.getAsync("key1").toCompletableFuture().join());
        assertEquals(result, cache2.getAsync("key1").toCompletableFuture().join());

        // when
        facade.invalidateAllAsync().toCompletableFuture().join();

        // then
        assertNull(facade.getAsync("key1").toCompletableFuture().join());
        assertNull(cache1.getAsync("key1").toCompletableFuture().join());
        assertNull(cache2.getAsync("key1").toCompletableFuture().join());
    }
}
