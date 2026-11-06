package io.koraframework.cache.redis;

import io.koraframework.test.redis.RedisParams;
import io.koraframework.test.redis.RedisTestContainer;
import io.lettuce.core.FlushMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@RedisTestContainer
class SyncCacheTests extends AbstractSyncCacheTests {

    @BeforeEach
    void setup(RedisParams redisParams) throws Exception {
        this.redisParams = redisParams;
        redisParams.execute(cmd -> cmd.flushall(FlushMode.SYNC));
        if (cache == null) {
            cache = createCache(redisParams);
        }
    }

    @Test
    void operationsAreDisabledWhenConfigDisabled() throws Exception {
        // given
        var disabledCache = createCacheDisabled(redisParams);

        // when
        assertEquals("1", disabledCache.put("1", "1"));
        assertEquals(Map.of("2", "2"), disabledCache.put(Map.of("2", "2")));

        // then
        assertNull(disabledCache.get("1"));
        assertTrue(disabledCache.get(List.of("1", "2")).isEmpty());

        // when
        assertEquals("3", disabledCache.computeIfAbsent("3", k -> "3"));
        assertEquals(Map.of("4", "4"), disabledCache.computeIfAbsent(Set.of("4"), keys -> Map.of("4", "4")));

        // then
        assertNull(disabledCache.get("3"));
        assertTrue(disabledCache.get(List.of("4")).isEmpty());
    }

    @Test
    void invalidateAllRemovesEveryKeyWithPrefix() {
        // given
        var values = new HashMap<String, String>();
        for (int i = 0; i < 250; i++) {
            values.put(String.valueOf(i), String.valueOf(i));
        }
        cache.put(values);

        // when
        cache.invalidateAll();

        // then
        assertEquals(0, redisParams.execute(cmd -> cmd.keys(PREFIX + ":*")).size());
    }

    @Test
    void invalidateAllRemovesKeysAmongUnrelatedKeys() {
        // given
        redisParams.execute(cmd -> {
            for (int i = 0; i < 200; i++) {
                cmd.set("other:" + i, "x");
            }
            return null;
        });
        cache.put("1", "1");
        cache.put("2", "2");

        // when
        cache.invalidateAll();

        // then
        assertNull(cache.get("1"));
        assertNull(cache.get("2"));
        assertEquals(200, redisParams.execute(cmd -> cmd.keys("other:*")).size());
    }
}
