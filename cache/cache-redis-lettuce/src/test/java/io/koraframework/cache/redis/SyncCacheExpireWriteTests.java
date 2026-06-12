package io.koraframework.cache.redis;

import io.koraframework.test.redis.RedisParams;
import io.koraframework.test.redis.RedisTestContainer;
import io.lettuce.core.FlushMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;

import java.time.Duration;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@RedisTestContainer
class SyncCacheExpireWriteTests extends AbstractSyncCacheTests {

    @BeforeEach
    void setup(RedisParams redisParams) throws Exception {
        this.redisParams = redisParams;
        redisParams.execute(cmd -> cmd.flushall(FlushMode.SYNC));
        if (cache == null) {
            cache = createCacheExpireWrite(redisParams, Duration.ofSeconds(1));
        }
    }
}
