package io.koraframework.cache.redis;

import io.lettuce.core.FlushMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import io.koraframework.test.redis.RedisParams;
import io.koraframework.test.redis.RedisTestContainer;

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
}
