package ru.tinkoff.kora.cache.caffeine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import ru.tinkoff.kora.cache.caffeine.testdata.DummyCache;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SyncCacheTests extends CacheRunner {

    private final DummyCache cache = createCache();

    @BeforeEach
    void reset() {
        cache.invalidateAll();
    }

    @Test
    void getWhenCacheEmpty() {
        // given
        var key = "1";

        // when
        assertNull(cache.get(key));
    }

    @Test
    void getWhenCacheFilled() {
        // given
        var key = "1";
        var value = "1";

        // when
        cache.put(key, value);

        // then
        final String fromCache = cache.get(key);
        assertEquals(value, fromCache);
    }

    @Test
    void getWrongKeyWhenCacheFilled() {
        // given
        var key = "1";
        var value = "1";

        // when
        cache.put(key, value);

        // then
        final String fromCache = cache.get("2");
        assertNull(fromCache);
    }

    @Test
    void getWhenCacheInvalidate() {
        // given
        var key = "1";
        var value = "1";
        cache.put(key, value);

        // when
        cache.invalidate(key);

        // then
        final String fromCache = cache.get(key);
        assertNull(fromCache);
    }

    @Test
    void getFromCacheWhenCacheInvalidateAll() {
        // given
        var key = "1";
        var value = "1";
        cache.put(key, value);

        // when
        cache.invalidateAll();

        // then
        final String fromCache = cache.get(key);
        assertNull(fromCache);
    }
}
