package io.koraframework.cache.redis;

import io.koraframework.cache.redis.testdata.DummyCache;
import io.koraframework.test.redis.RedisParams;
import io.koraframework.test.redis.RedisTestContainer;

import java.time.Duration;

@RedisTestContainer
class SyncCacheExpireReadTests extends AbstractSyncCacheTests {

    @Override
    protected DummyCache initCache(RedisParams redisParams, String prefix, int dbIndex) throws Exception {
        return createCacheExpireRead(redisParams, prefix, dbIndex, Duration.ofSeconds(1));
    }
}
