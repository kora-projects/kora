package ru.tinkoff.kora.cache.redis;

import io.lettuce.core.FlushMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import ru.tinkoff.kora.cache.redis.testdata.DummyCache;
import ru.tinkoff.kora.test.redis.RedisParams;
import ru.tinkoff.kora.test.redis.RedisTestContainer;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@RedisTestContainer
class SyncCacheTests extends CacheRunner {

    private DummyCache cache = null;

    @BeforeEach
    void setup(RedisParams redisParams) {
        redisParams.execute(cmd -> cmd.flushall(FlushMode.SYNC));
        if (cache == null) {
            cache = createCache(redisParams);
        }
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
