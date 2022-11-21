package ru.tinkoff.kora.cache.redis;

import ru.tinkoff.kora.application.graph.TypeRef;
import ru.tinkoff.kora.cache.CacheManager;
import ru.tinkoff.kora.cache.redis.client.ReactiveRedisClient;
import ru.tinkoff.kora.cache.redis.client.SyncRedisClient;
import ru.tinkoff.kora.cache.telemetry.CacheTelemetry;
import ru.tinkoff.kora.common.Tag;

public interface DefaultRedisCacheModule extends RedisCacheModule {

    default <K, V> CacheManager<K, V> defaultRedisCacheManager(RedisKeyMapper<K> keyMapper,
                                                                                RedisValueMapper<V> valueMapper,
                                                                                SyncRedisClient syncClient,
                                                                                ReactiveRedisClient reactiveClient,
                                                                                RedisCacheConfig cacheConfig,
                                                                                @Tag(RedisCacheManager.class) CacheTelemetry telemetry,
                                                                                TypeRef<K> keyRef,
                                                                                TypeRef<V> valueRef) {
        return taggedRedisCacheManager(keyMapper, valueMapper, syncClient, reactiveClient, cacheConfig, telemetry, keyRef, valueRef);
    }
}
