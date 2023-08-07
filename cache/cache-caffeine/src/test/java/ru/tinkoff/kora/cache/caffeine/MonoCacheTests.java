package ru.tinkoff.kora.cache.caffeine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import ru.tinkoff.kora.cache.caffeine.testdata.DummyCache;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MonoCacheTests extends CacheRunner {

    private final DummyCache cache = createCache();

    @BeforeEach
    void reset() {
        cache.invalidateAllAsync().block();
    }

    @Test
    void getWhenCacheEmpty() {
        // given
        var key = "1";

        // when
        assertNull(cache.getAsync(key).block());
    }

    @Test
    void getWhenCacheFilled() {
        // given
        var key = "1";
        var value = "1";

        // when
        cache.putAsync(key, value).block();

        // then
        final String fromCache = cache.getAsync(key).block();
        assertEquals(value, fromCache);
    }

    @Test
    void getWrongKeyWhenCacheFilled() {
        // given
        var key = "1";
        var value = "1";

        // when
        cache.putAsync(key, value).block();

        // then
        final String fromCache = cache.getAsync("2").block();
        assertNull(fromCache);
    }

    @Test
    void getWhenCacheInvalidate() {
        // given
        var key = "1";
        var value = "1";
        cache.putAsync(key, value).block();

        // when
        cache.invalidateAsync(key).block();

        // then
        final String fromCache = cache.getAsync(key).block();
        assertNull(fromCache);
    }

    @Test
    void getFromCacheWhenCacheInvalidateAll() {
        // given
        var key = "1";
        var value = "1";
        cache.putAsync(key, value).block();

        // when
        cache.invalidateAllAsync().block();

        // then
        final String fromCache = cache.getAsync(key).block();
        assertNull(fromCache);
    }
}
