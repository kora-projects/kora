package ru.tinkoff.kora.cache.redis;

import io.lettuce.core.FlushMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import ru.tinkoff.kora.cache.redis.client.SyncRedisClient;
import ru.tinkoff.kora.cache.redis.testdata.Box;
import ru.tinkoff.kora.cache.redis.testdata.CacheableMockLifecycle;
import ru.tinkoff.kora.cache.redis.testdata.CacheableTargetSync;
import ru.tinkoff.kora.test.redis.RedisParams;
import ru.tinkoff.kora.test.redis.RedisTestContainer;

import java.math.BigDecimal;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@RedisTestContainer
class SyncCacheAopTests extends CacheRunner {

    private SyncRedisClient syncRedisClient = null;
    private CacheableTargetSync service = null;

    private CacheableTargetSync getService() {
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

            service = values.stream()
                .filter(a -> a instanceof CacheableMockLifecycle)
                .map(a -> ((CacheableMockLifecycle) a).sync())
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

    @Test
    void getFromCacheWhenWasCacheEmpty() {
        // given
        final CacheableTargetSync service = getService();
        service.number = "1";
        assertNotNull(service);

        // when
        final Box notCached = service.getValue("1", BigDecimal.ZERO);
        service.number = "2";

        // then
        final Box fromCache = service.getValue("1", BigDecimal.ZERO);
        assertEquals(notCached, fromCache);
        assertNotEquals("2", fromCache.number());

        // cleanup
        service.evictAll();
    }

    @Test
    void getFromCacheWhenCacheFilled() {
        // given
        final CacheableTargetSync service = getService();
        service.number = "1";
        assertNotNull(service);

        // when
        final Box initial = service.getValue("1", BigDecimal.ZERO);
        final Box cached = service.putValue(BigDecimal.ZERO, "5", "1");
        assertEquals(initial, cached);
        service.number = "2";

        // then
        final Box fromCache = service.getValue("1", BigDecimal.ZERO);
        assertEquals(cached, fromCache);

        // cleanup
        service.evictAll();
    }

    @Test
    void getFromCacheWrongKeyWhenCacheFilled() {
        // given
        final CacheableTargetSync service = getService();
        service.number = "1";
        assertNotNull(service);

        // when
        final Box initial = service.getValue("1", BigDecimal.ZERO);
        final Box cached = service.putValue(BigDecimal.ZERO, "5", "1");
        assertEquals(initial, cached);
        service.number = "2";

        // then
        final Box fromCache = service.getValue("2", BigDecimal.ZERO);
        assertNotEquals(cached, fromCache);
        assertEquals(service.number, fromCache.number());

        // cleanup
        service.evictAll();
    }

    @Test
    void getFromCacheWhenCacheFilledOtherKey() {
        // given
        final CacheableTargetSync service = getService();
        service.number = "1";
        assertNotNull(service);

        // when
        final Box cached = service.putValue(BigDecimal.ZERO, "5", "1");
        service.number = "2";
        final Box initial = service.getValue("2", BigDecimal.ZERO);
        assertNotEquals(cached, initial);

        // then
        final Box fromCache = service.getValue("2", BigDecimal.ZERO);
        assertNotEquals(cached, fromCache);
        assertEquals(initial, fromCache);

        // cleanup
        service.evictAll();
    }

    @Test
    void getFromCacheWhenCacheInvalidate() {
        // given
        final CacheableTargetSync service = getService();
        service.number = "1";
        assertNotNull(service);

        // when
        final Box initial = service.getValue("1", BigDecimal.ZERO);
        final Box cached = service.putValue(BigDecimal.ZERO, "5", "1");
        assertEquals(initial, cached);
        service.number = "2";
        service.evictValue("1", BigDecimal.ZERO);

        // then
        final Box fromCache = service.getValue("1", BigDecimal.ZERO);
        assertNotEquals(cached, fromCache);

        // cleanup
        service.evictAll();
    }

    @Test
    void getFromCacheWhenCacheInvalidateAll() {
        // given
        final CacheableTargetSync service = getService();
        service.number = "1";
        assertNotNull(service);

        // when
        final Box initial = service.getValue("1", BigDecimal.ZERO);
        final Box cached = service.putValue(BigDecimal.ZERO, "5", "1");
        assertEquals(initial, cached);
        service.number = "2";
        service.evictAll();

        // then
        final Box fromCache = service.getValue("1", BigDecimal.ZERO);
        assertNotEquals(cached, fromCache);

        // cleanup
        service.evictAll();
    }
}
