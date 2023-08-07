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
class MonoCacheTests extends CacheRunner {

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
