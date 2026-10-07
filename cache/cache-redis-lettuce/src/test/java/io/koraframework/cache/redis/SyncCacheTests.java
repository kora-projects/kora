package io.koraframework.cache.redis;

import io.koraframework.test.redis.RedisParams;
import io.koraframework.test.redis.RedisTestContainer;
import io.lettuce.core.FlushMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

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
    void invalidateAllTreatsKeyPrefixLiterally() throws Exception {
        for (var prefix : List.of("v?", "v*", "v[1]", "v\\1")) {
            // given
            redisParams.execute(cmd -> cmd.flushall(FlushMode.SYNC));
            var globCache = createCache(redisParams, prefix);
            globCache.put("k", "drop");
            redisParams.execute(cmd -> cmd.set("v1:k", "keep"));

            // when
            globCache.invalidateAll();

            // then
            assertNull(globCache.get("k"), prefix);
            assertEquals("keep", redisParams.execute(cmd -> cmd.get("v1:k")), prefix);
        }
    }
}
