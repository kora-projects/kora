package io.koraframework.cache.redis;

import io.koraframework.cache.redis.testdata.DummyCache;
import io.koraframework.test.redis.RedisParams;
import io.koraframework.test.redis.RedisTestContainer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RedisTestContainer
class SyncCacheTests extends AbstractSyncCacheTests {

    @Override
    protected DummyCache initCache(RedisParams redisParams, String prefix, int dbIndex) throws Exception {
        return createCache(redisParams, prefix, dbIndex);
    }

    @Test
    void operationsAreDisabledWhenConfigDisabled() throws Exception {
        // given
        String disabledPrefix = "test-disabled:" + UUID.randomUUID().toString().substring(0, 8);
        var disabledCache = createCacheDisabled(redisParams, disabledPrefix, currentDbIndex);

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
}
