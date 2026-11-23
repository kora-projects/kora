package io.koraframework.cache.redis;

import io.koraframework.test.redis.RedisParams;
import io.koraframework.test.redis.RedisTestContainer;
import io.lettuce.core.FlushMode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@RedisTestContainer
class SyncCacheMetricsTests extends CacheRunner {

    private RedisParams redisParams;

    @BeforeEach
    void setup(RedisParams redisParams) {
        this.redisParams = redisParams;
        redisParams.execute(cmd -> cmd.flushall(FlushMode.SYNC));
    }

    @Test
    void getManyReportsHitsAndMissesPerKey() throws Exception {
        // given
        var registry = new SimpleMeterRegistry();
        var cache = createCacheWithMetrics(redisParams, registry);
        cache.put("a", "A");

        // when
        assertEquals(Map.of("a", "A"), cache.get(List.of("a", "b", "c")));

        // then
        assertEquals(1.0, count(registry, "GET_MANY", "hit"));
        assertEquals(2.0, count(registry, "GET_MANY", "miss"));
    }

    @Test
    void computeIfAbsentManyReportsComputedValuesAsMisses() throws Exception {
        // given
        var registry = new SimpleMeterRegistry();
        var cache = createCacheWithMetrics(redisParams, registry);
        cache.put("a", "A");

        // when
        var result = cache.computeIfAbsent(List.of("a", "b", "c"), keys -> keys.stream()
            .collect(Collectors.toMap(Function.identity(), String::toUpperCase)));

        // then
        assertEquals(Map.of("a", "A", "b", "B", "c", "C"), result);
        assertEquals(1.0, count(registry, "COMPUTE_IF_ABSENT_MANY", "hit"));
        assertEquals(2.0, count(registry, "COMPUTE_IF_ABSENT_MANY", "miss"));
    }

    private static double count(SimpleMeterRegistry registry, String operation, String result) {
        var counter = registry.find("cache.requests")
            .tag("cache.operation", operation)
            .tag("cache.result", result)
            .counter();
        return counter == null ? 0.0 : counter.count();
    }
}
