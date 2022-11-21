package ru.tinkoff.kora.cache.redis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.cache.Cache;
import ru.tinkoff.kora.cache.CacheManager;
import ru.tinkoff.kora.cache.redis.client.ReactiveRedisClient;
import ru.tinkoff.kora.cache.redis.client.SyncRedisClient;
import ru.tinkoff.kora.cache.telemetry.CacheTelemetry;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class RedisCacheManager<K, V> implements CacheManager<K, V> {

    private static final Logger logger = LoggerFactory.getLogger(RedisCacheManager.class);

    private final Map<String, Cache<K, V>> cacheMap = new ConcurrentHashMap<>();

    private final SyncRedisClient syncClient;
    private final ReactiveRedisClient reactiveClient;
    private final RedisCacheConfig cacheConfig;
    private final CacheTelemetry telemetry;

    private final RedisKeyMapper<K> keyMapper;
    private final RedisValueMapper<V> valueMapper;

    RedisCacheManager(SyncRedisClient syncClient,
                      ReactiveRedisClient reactiveClient,
                      RedisCacheConfig cacheConfig,
                      CacheTelemetry telemetry,
                      RedisKeyMapper<K> keyMapper,
                      RedisValueMapper<V> valueMapper) {
        this.syncClient = syncClient;
        this.reactiveClient = reactiveClient;
        this.cacheConfig = cacheConfig;
        this.telemetry = telemetry;
        this.keyMapper = keyMapper;
        this.valueMapper = valueMapper;
    }

    @Nonnull
    @Override
    public Cache<K, V> getCache(@Nonnull String name) {
        return cacheMap.computeIfAbsent(name, k -> build(name));
    }

    private RedisCache<K, V> build(@Nonnull String name) {
        logger.trace("Build cache for name: {}", name);
        final RedisCacheConfig.NamedCacheConfig config = cacheConfig.getByName(name);
        return new RedisCache<>(name, syncClient, reactiveClient, telemetry, keyMapper, valueMapper, config);
    }
}
