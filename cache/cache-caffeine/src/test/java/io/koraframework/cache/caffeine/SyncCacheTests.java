package io.koraframework.cache.caffeine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import io.koraframework.cache.caffeine.testdata.DummyCache;

import java.util.List;
import java.util.Map;
import java.util.Set;

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

    @Test
    void operationsAreDisabledWhenConfigDisabled() {
        // given
        var disabledCache = createCache(false);

        // when
        assertEquals("1", disabledCache.put("1", "1"));
        assertEquals(Map.of("2", "2"), disabledCache.put(Map.of("2", "2")));

        // then
        assertNull(disabledCache.get("1"));
        assertTrue(disabledCache.get(List.of("1", "2")).isEmpty());
        assertTrue(disabledCache.getAll().isEmpty());

        // when
        assertEquals("3", disabledCache.computeIfAbsent("3", k -> "3"));
        assertEquals(Map.of("4", "4"), disabledCache.computeIfAbsent(Set.of("4"), keys -> Map.of("4", "4")));

        // then
        assertNull(disabledCache.get("3"));
        assertTrue(disabledCache.get(List.of("4")).isEmpty());
    }
}
