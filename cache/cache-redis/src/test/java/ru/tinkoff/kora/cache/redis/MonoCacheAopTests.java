package ru.tinkoff.kora.cache.redis;

import io.lettuce.core.FlushMode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import ru.tinkoff.kora.cache.redis.client.LettuceBasicCommands;
import ru.tinkoff.kora.cache.redis.client.SyncRedisClient;
import ru.tinkoff.kora.cache.redis.testdata.Box;
import ru.tinkoff.kora.cache.redis.testdata.CacheableMockLifecycle;
import ru.tinkoff.kora.cache.redis.testdata.CacheableTargetMono;
import ru.tinkoff.kora.test.redis.RedisParams;
import ru.tinkoff.kora.test.redis.RedisTestContainer;

import java.math.BigDecimal;
import java.time.Duration;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@RedisTestContainer
class MonoCacheAopTests extends CacheRunner {

    private static LettuceBasicCommands commands = null;
    private SyncRedisClient syncRedisClient = null;
    private CacheableTargetMono service = null;

    private CacheableTargetMono getService() {
        if (service != null) {
            return service;
        }

        try {
            var graphDraw = createGraphDraw();
            var graph = graphDraw.init().block();
            var values = graphDraw.getNodes()
                .stream()
                .map(graph::get)
                .toList();

            syncRedisClient = values.stream()
                .filter(a1 -> a1 instanceof SyncRedisClient)
                .map(a1 -> ((SyncRedisClient) a1))
                .findFirst().orElseThrow();

            commands = values.stream()
                .filter(a1 -> a1 instanceof LettuceBasicCommands)
                .map(a1 -> ((LettuceBasicCommands) a1))
                .findFirst().orElseThrow();

            service = values.stream()
                .filter(a -> a instanceof CacheableMockLifecycle)
                .map(a -> ((CacheableMockLifecycle) a).mono())
                .findFirst().orElseThrow();
            return service;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @BeforeEach
    void setupRedis(RedisParams redisParams) {
        CacheRunner.redisUri = redisParams.uri();
        redisParams.execute(cmd -> cmd.flushall(FlushMode.SYNC));
    }

    @AfterAll
    static void cleanup() {
        if (commands != null) {
            commands.close();
        }
    }

    @Test
    void getFromCacheWhenWasCacheEmpty() {
        // given
        final CacheableTargetMono service = getService();
        service.number = "1";
        assertNotNull(service);

        // when
        final Box notCached = service.getValue("1", BigDecimal.ZERO).block(Duration.ofMinutes(1));
        service.number = "2";

        // then
        final Box fromCache = service.getValue("1", BigDecimal.ZERO).block(Duration.ofMinutes(1));
        assertNotNull(fromCache);
        assertEquals(notCached, fromCache);
        assertNotEquals("2", fromCache.number());

        // cleanup
        service.evictAll().block(Duration.ofMinutes(1));
    }

    @Test
    void getFromCacheWhenCacheFilled() {
        // given
        final CacheableTargetMono service = getService();
        service.number = "1";
        assertNotNull(service);

        // when
        final Box initial = service.getValue("1", BigDecimal.ZERO).block(Duration.ofMinutes(1));
        final Box cached = service.putValue(BigDecimal.ZERO, "5", "1").block(Duration.ofMinutes(1));
        assertEquals(initial, cached);
        service.number = "2";

        // then
        final Box fromCache = service.getValue("1", BigDecimal.ZERO).block(Duration.ofMinutes(1));
        assertEquals(cached, fromCache);

        // cleanup
        service.evictAll().block(Duration.ofMinutes(1));
    }

    @Test
    void getFromCacheWrongKeyWhenCacheFilled() {
        // given
        final CacheableTargetMono service = getService();
        service.number = "1";
        assertNotNull(service);

        // when
        final Box initial = service.getValue("1", BigDecimal.ZERO).block(Duration.ofMinutes(1));
        final Box cached = service.putValue(BigDecimal.ZERO, "5", "1").block(Duration.ofMinutes(1));
        assertEquals(initial, cached);
        service.number = "2";

        // then
        final Box fromCache = service.getValue("2", BigDecimal.ZERO).block(Duration.ofMinutes(1));
        assertNotEquals(cached, fromCache);
        assertEquals(service.number, fromCache.number());

        // cleanup
        service.evictAll().block(Duration.ofMinutes(1));
    }

    @Test
    void getFromCacheWhenCacheFilledOtherKey() {
        // given
        final CacheableTargetMono service = getService();
        service.number = "1";
        assertNotNull(service);

        // when
        final Box cached = service.putValue(BigDecimal.ZERO, "5", "1").block(Duration.ofMinutes(1));
        service.number = "2";
        final Box initial = service.getValue("2", BigDecimal.ZERO).block(Duration.ofMinutes(1));
        assertNotEquals(cached, initial);

        // then
        final Box fromCache = service.getValue("2", BigDecimal.ZERO).block(Duration.ofMinutes(1));
        assertNotEquals(cached, fromCache);
        assertEquals(initial, fromCache);

        // cleanup
        service.evictAll().block(Duration.ofMinutes(1));
    }

    @Test
    void getFromCacheWhenCacheInvalidate() {
        // given
        final CacheableTargetMono service = getService();
        service.number = "1";
        assertNotNull(service);

        // when
        final Box initial = service.getValue("1", BigDecimal.ZERO).block(Duration.ofMinutes(1));
        final Box cached = service.putValue(BigDecimal.ZERO, "5", "1").block(Duration.ofMinutes(1));
        assertEquals(initial, cached);
        service.number = "2";
        service.evictValue("1", BigDecimal.ZERO).block(Duration.ofMinutes(1));

        // then
        final Box fromCache = service.getValue("1", BigDecimal.ZERO).block(Duration.ofMinutes(1));
        assertNotEquals(cached, fromCache);

        // cleanup
        service.evictAll().block(Duration.ofMinutes(1));
    }

    @Test
    void getFromCacheWhenCacheInvalidateAll() {
        // given
        final CacheableTargetMono service = getService();
        service.number = "1";
        assertNotNull(service);

        // when
        final Box initial = service.getValue("1", BigDecimal.ZERO).block(Duration.ofMinutes(1));
        final Box cached = service.putValue(BigDecimal.ZERO, "5", "1").block(Duration.ofMinutes(1));
        assertEquals(initial, cached);
        service.number = "2";
        service.evictAll().block(Duration.ofMinutes(1));

        // then
        final Box fromCache = service.getValue("1", BigDecimal.ZERO).block(Duration.ofMinutes(1));
        assertNotEquals(cached, fromCache);

        // cleanup
        service.evictAll().block(Duration.ofMinutes(1));
    }
}
