package io.koraframework.cache.redis;

import io.koraframework.test.redis.RedisParams;
import io.koraframework.test.redis.RedisTestContainer;
import io.lettuce.core.FlushMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.time.Duration;
import java.util.Map;

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

    @Test
    void putSetsTtlInMillis() {
        // when
        cache.put("1", "1");

        // then
        assertTtlAtMostOneSecond("1");
    }

    @Test
    void putManySetsTtlInMillis() {
        // when
        cache.put(Map.of("1", "1", "2", "2"));

        // then
        assertTtlAtMostOneSecond("1");
        assertTtlAtMostOneSecond("2");
    }

    @Test
    void computeIfAbsentSetsTtlInMillis() {
        // when
        cache.computeIfAbsent("1", k -> "1");

        // then
        assertTtlAtMostOneSecond("1");
    }

    @Test
    void putExpireAfterWriteSetsTtlInMillis() {
        // when
        cache.putExpireAfterWrite("1", "1", Duration.ofMillis(500));

        // then
        long pttl = redisParams.execute(cmd -> cmd.pttl(PREFIX + ":1"));
        assertTrue(pttl > 0 && pttl <= 500, "PTTL expected <= 500ms but was " + pttl);
    }

    private void assertTtlAtMostOneSecond(String key) {
        long pttl = redisParams.execute(cmd -> cmd.pttl(PREFIX + ":" + key));
        assertTrue(pttl > 0 && pttl <= 1000, "PTTL expected <= 1000ms but was " + pttl);
    }
}
