package ru.tinkoff.kora.cache.redis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.cache.Cache;
import ru.tinkoff.kora.cache.redis.client.ReactiveRedisClient;
import ru.tinkoff.kora.cache.redis.client.SyncRedisClient;
import ru.tinkoff.kora.cache.telemetry.CacheTelemetry;

import javax.annotation.Nonnull;

final class RedisCache<K, V> implements Cache<K, V> {

    private static final Logger logger = LoggerFactory.getLogger(RedisCache.class);

    private final String name;
    private final SyncRedisClient syncClient;
    private final ReactiveRedisClient reactiveClient;
    private final CacheTelemetry telemetry;

    private final RedisKeyMapper<K> keyMapper;
    private final RedisValueMapper<V> valueMapper;

    private final Long expireAfterAccessMillis;
    private final Long expireAfterWriteMillis;

    RedisCache(String name,
               SyncRedisClient syncClient,
               ReactiveRedisClient reactiveClient,
               CacheTelemetry telemetry,
               RedisKeyMapper<K> keyMapper,
               RedisValueMapper<V> valueMapper,
               RedisCacheConfig.NamedCacheConfig cacheConfig) {
        this.name = name;
        this.syncClient = syncClient;
        this.reactiveClient = reactiveClient;
        this.telemetry = telemetry;
        this.keyMapper = keyMapper;
        this.valueMapper = valueMapper;
        this.expireAfterAccessMillis = (cacheConfig.expireAfterAccess() == null)
            ? null
            : cacheConfig.expireAfterAccess().toMillis();
        this.expireAfterWriteMillis = (cacheConfig.expireAfterWrite() == null)
            ? null
            : cacheConfig.expireAfterWrite().toMillis();
    }

    @Nonnull
    String origin() {
        return "redis";
    }

    @Override
    public V get(@Nonnull K key) {
        logger.trace("Looking for value in cache '{}' for key: {}", name, key);
        final byte[] keyAsBytes = keyMapper.apply(key);
        final CacheTelemetry.TelemetryContext telemetryContext = telemetry.create(CacheTelemetry.Operation.Type.GET, name, origin());

        try {
            telemetryContext.startRecording();
            final byte[] jsonAsBytes = (expireAfterAccessMillis == null)
                ? syncClient.get(keyAsBytes)
                : syncClient.getExpire(keyAsBytes, expireAfterAccessMillis);

            if (jsonAsBytes != null) {
                logger.trace("Value NOT found in cache '{}' for key: {}", name, key);
            } else {
                logger.debug("Value found in cache '{}' for key: {}", name, key);
            }

            final V v = valueMapper.read(jsonAsBytes);
            telemetryContext.recordSuccess(jsonAsBytes);
            return v;
        } catch (Exception e) {
            telemetryContext.recordFailure(e);
            logger.warn(e.getMessage(), e);
            return null;
        }
    }

    @Nonnull
    @Override
    public V put(@Nonnull K key, @Nonnull V value) {
        logger.trace("Putting value in cache '{}' for key: {}", name, key);
        final byte[] keyAsBytes = keyMapper.apply(key);
        final byte[] valueAsBytes = valueMapper.write(value);
        final CacheTelemetry.TelemetryContext telemetryContext = telemetry.create(CacheTelemetry.Operation.Type.PUT, name, origin());

        try {
            telemetryContext.startRecording();
            if (expireAfterWriteMillis == null) {
                syncClient.set(keyAsBytes, valueAsBytes);
            } else {
                syncClient.setExpire(keyAsBytes, valueAsBytes, expireAfterWriteMillis);
            }
            telemetryContext.recordSuccess();
            logger.trace("Putted value in cache '{}' for key: {}", name, key);
            return value;
        } catch (Exception e) {
            telemetryContext.recordFailure(e);
            logger.warn(e.getMessage(), e);
            return value;
        }
    }

    @Override
    public void invalidate(@Nonnull K key) {
        logger.trace("Invalidating value in cache '{}' for key: {}", name, key);
        final byte[] keyAsBytes = keyMapper.apply(key);
        final CacheTelemetry.TelemetryContext telemetryContext = telemetry.create(CacheTelemetry.Operation.Type.INVALIDATE, name, origin());

        try {
            telemetryContext.startRecording();
            syncClient.del(keyAsBytes);
            telemetryContext.recordSuccess();
            logger.trace("Invalidated value in cache '{}' for key: {}", name, key);
        } catch (Exception e) {
            telemetryContext.recordFailure(e);
            logger.warn(e.getMessage(), e);
        }
    }

    @Override
    public void invalidateAll() {
        logger.trace("Invalidating all values in cache '{}'", name);
        final CacheTelemetry.TelemetryContext telemetryContext = telemetry.create(CacheTelemetry.Operation.Type.INVALIDATE_ALL, name, origin());

        try {
            telemetryContext.startRecording();
            syncClient.flushAll();
            telemetryContext.recordSuccess();
            logger.trace("Invalidated all values in cache '{}'", name);
        } catch (Exception e) {
            telemetryContext.recordFailure(e);
            logger.warn(e.getMessage(), e);
        }
    }

    @Nonnull
    @Override
    public Mono<V> getAsync(@Nonnull K key) {
        final CacheTelemetry.TelemetryContext telemetryContext = telemetry.create(CacheTelemetry.Operation.Type.GET, name, origin());
        return Mono.fromCallable(() -> keyMapper.apply(key))
            .flatMap(keyAsBytes -> {
                logger.trace("Looking for value in cache '{}' for key: {}", name, key);
                telemetryContext.startRecording();
                return (expireAfterAccessMillis == null)
                    ? reactiveClient.get(keyAsBytes)
                    : reactiveClient.getExpire(keyAsBytes, expireAfterAccessMillis);
            })
            .map(jsonAsBytes -> {
                if (jsonAsBytes != null) {
                    logger.trace("Value NOT found in cache '{}' for key: {}", name, key);
                } else {
                    logger.debug("Value found in cache '{}' for key: {}", name, key);
                }
                final V v = valueMapper.read(jsonAsBytes);
                telemetryContext.recordSuccess(jsonAsBytes);
                return v;
            })
            .onErrorResume(e -> {
                telemetryContext.recordFailure(e);
                logger.warn(e.getMessage(), e);
                return Mono.empty();
            });
    }

    @Nonnull
    @Override
    public Mono<V> putAsync(@Nonnull K key, @Nonnull V value) {
        final CacheTelemetry.TelemetryContext telemetryContext = telemetry.create(CacheTelemetry.Operation.Type.PUT, name, origin());
        return Mono.fromCallable(() -> keyMapper.apply(key))
            .flatMap(keyAsBytes -> {
                logger.trace("Putting value in cache '{}' for key: {}", name, key);
                final byte[] valueAsBytes = valueMapper.write(value);
                telemetryContext.startRecording();
                return (expireAfterWriteMillis == null)
                    ? reactiveClient.set(keyAsBytes, valueAsBytes)
                    : reactiveClient.setExpire(keyAsBytes, valueAsBytes, expireAfterWriteMillis);
            })
            .map(r -> value)
            .switchIfEmpty(Mono.fromCallable(() -> {
                telemetryContext.recordSuccess();
                logger.trace("Putted value in cache '{}' for key: {}", name, key);
                return value;
            }))
            .onErrorResume(e -> {
                telemetryContext.recordFailure(e);
                logger.warn(e.getMessage(), e);
                return Mono.just(value);
            });
    }

    @Nonnull
    @Override
    public Mono<Void> invalidateAsync(@Nonnull K key) {
        final CacheTelemetry.TelemetryContext telemetryContext = telemetry.create(CacheTelemetry.Operation.Type.INVALIDATE, name, origin());
        return Mono.fromCallable(() -> {
                logger.trace("Invalidating value in cache '{}' for key: {}", name, key);
                return keyMapper.apply(key);
            })
            .flatMap(reactiveClient::del)
            .doOnSuccess(r -> telemetryContext.recordSuccess())
            .onErrorResume(e -> {
                telemetryContext.recordFailure(e);
                logger.warn(e.getMessage(), e);
                return Mono.empty();
            });
    }

    @Nonnull
    @Override
    public Mono<Void> invalidateAllAsync() {
        final CacheTelemetry.TelemetryContext telemetryContext = telemetry.create(CacheTelemetry.Operation.Type.INVALIDATE_ALL, name, origin());
        logger.trace("Invalidating all values in cache '{}'", name);
        return reactiveClient.flushAll()
            .doOnSuccess(r -> telemetryContext.recordSuccess())
            .onErrorResume(e -> {
                telemetryContext.recordFailure(e);
                logger.warn(e.getMessage(), e);
                return Mono.empty();
            });
    }
}
