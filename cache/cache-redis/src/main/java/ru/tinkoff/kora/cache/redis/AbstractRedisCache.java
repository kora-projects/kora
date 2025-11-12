package ru.tinkoff.kora.cache.redis;

import io.opentelemetry.context.Context;
import jakarta.annotation.Nonnull;
import ru.tinkoff.kora.cache.Cache;
import ru.tinkoff.kora.cache.redis.telemetry.RedisCacheTelemetry;
import ru.tinkoff.kora.cache.redis.telemetry.RedisCacheTelemetryFactory;
import ru.tinkoff.kora.common.telemetry.Observation;
import ru.tinkoff.kora.common.telemetry.OpentelemetryContext;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletionException;
import java.util.function.Function;
import java.util.stream.Collectors;

public abstract class AbstractRedisCache<K, V> implements Cache<K, V> {
    private final RedisCacheClient redisClient;
    private final RedisCacheTelemetry telemetry;
    private final byte[] keyPrefix;

    private final RedisCacheKeyMapper<K> keyMapper;
    private final RedisCacheValueMapper<V> valueMapper;

    private final Long expireAfterAccessMillis;
    private final Long expireAfterWriteMillis;

    protected AbstractRedisCache(String name,
                                 RedisCacheConfig config,
                                 RedisCacheClient redisClient,
                                 RedisCacheTelemetryFactory telemetryFactory,
                                 RedisCacheKeyMapper<K> keyMapper,
                                 RedisCacheValueMapper<V> valueMapper) {
        this.redisClient = redisClient;
        this.telemetry = telemetryFactory.get(redisClient.config(), config, name);
        this.keyMapper = keyMapper;
        this.valueMapper = valueMapper;
        this.expireAfterAccessMillis = (config.expireAfterAccess() == null)
            ? null
            : config.expireAfterAccess().toMillis();
        this.expireAfterWriteMillis = (config.expireAfterWrite() == null)
            ? null
            : config.expireAfterWrite().toMillis();

        if (config.keyPrefix().isEmpty()) {
            this.keyPrefix = null;
        } else {
            var prefixRaw = config.keyPrefix().getBytes(StandardCharsets.UTF_8);
            this.keyPrefix = new byte[prefixRaw.length + RedisCacheKeyMapper.DELIMITER.length];
            System.arraycopy(prefixRaw, 0, this.keyPrefix, 0, prefixRaw.length);
            System.arraycopy(RedisCacheKeyMapper.DELIMITER, 0, this.keyPrefix, prefixRaw.length, RedisCacheKeyMapper.DELIMITER.length);
        }
    }

    @Override
    public V get(@Nonnull K key) {
        if (key == null) {
            return null;
        }

        var observation = this.telemetry.observe("GET");
        return ScopedValue
            .where(Observation.VALUE, observation)
            .where(OpentelemetryContext.VALUE, Context.current().with(observation.span()))
            .call(() -> {
                observation.observeKey(key);
                try {
                    final byte[] keyAsBytes = mapKey(key);
                    final byte[] jsonAsBytes = (expireAfterAccessMillis == null)
                        ? redisClient.get(keyAsBytes).toCompletableFuture().join()
                        : redisClient.getex(keyAsBytes, expireAfterAccessMillis).toCompletableFuture().join();

                    final V value = valueMapper.read(jsonAsBytes);
                    observation.observeValue(value);
                    return value;
                } catch (CompletionException e) {
                    observation.observeError(e.getCause());
                    return null;
                } catch (Exception e) {
                    observation.observeError(e);
                    return null;
                } finally {
                    observation.end();
                }
            });
    }

    @Nonnull
    @Override
    public Map<K, V> get(@Nonnull Collection<K> keys) {
        if (keys == null || keys.isEmpty()) {
            return Collections.emptyMap();
        }

        var observation = this.telemetry.observe("GET_MANY");
        return ScopedValue
            .where(Observation.VALUE, observation)
            .where(OpentelemetryContext.VALUE, Context.current().with(observation.span()))
            .call(() -> {
                try {
                    observation.observeKeys(keys);
                    final Map<K, byte[]> keysByKeyBytes = keys.stream()
                        .collect(Collectors.toMap(k -> k, this::mapKey, (v1, v2) -> v2));

                    final byte[][] keysByBytes = keysByKeyBytes.values().toArray(byte[][]::new);
                    final Map<byte[], byte[]> valueByKeys = (expireAfterAccessMillis == null)
                        ? redisClient.mget(keysByBytes).toCompletableFuture().join()
                        : redisClient.getex(keysByBytes, expireAfterAccessMillis).toCompletableFuture().join();

                    final Map<K, V> keyToValue = new HashMap<>();
                    for (var entry : keysByKeyBytes.entrySet()) {
                        valueByKeys.forEach((k, v) -> {
                            if (Arrays.equals(entry.getValue(), k)) {
                                var value = valueMapper.read(v);
                                keyToValue.put(entry.getKey(), value);
                            }
                        });
                    }

                    observation.observeValues(keyToValue);
                    return keyToValue;
                } catch (CompletionException e) {
                    observation.observeError(e.getCause());
                    return Collections.emptyMap();
                } catch (Exception e) {
                    observation.observeError(e);
                    return Collections.emptyMap();
                }
            });
    }

    @Nonnull
    @Override
    public V put(@Nonnull K key, @Nonnull V value) {
        if (key == null || value == null) {
            return null;
        }
        var observation = this.telemetry.observe("PUT");
        return ScopedValue
            .where(Observation.VALUE, observation)
            .where(OpentelemetryContext.VALUE, Context.current().with(observation.span()))
            .call(() -> {
                observation.observeKey(key);
                observation.observeValue(value);
                try {
                    final byte[] keyAsBytes = mapKey(key);
                    final byte[] valueAsBytes = valueMapper.write(value);
                    if (expireAfterWriteMillis == null) {
                        redisClient.set(keyAsBytes, valueAsBytes).toCompletableFuture().join();
                    } else {
                        redisClient.psetex(keyAsBytes, valueAsBytes, expireAfterWriteMillis).toCompletableFuture().join();
                    }
                    return value;
                } catch (CompletionException e) {
                    observation.observeError(e.getCause());
                    return value;
                } catch (Exception e) {
                    observation.observeError(e);
                    return value;
                } finally {
                    observation.end();
                }
            });
    }

    @Nonnull
    @Override
    public Map<K, V> put(@Nonnull Map<K, V> keyAndValues) {
        if (keyAndValues == null || keyAndValues.isEmpty()) {
            return Collections.emptyMap();
        }

        var observation = this.telemetry.observe("PUT_MANY");
        return ScopedValue
            .where(Observation.VALUE, observation)
            .where(OpentelemetryContext.VALUE, Context.current().with(observation.span()))
            .call(() -> {
                observation.observeKeys(keyAndValues.keySet());
                observation.observeValues(keyAndValues);
                try {
                    var keyAndValuesAsBytes = new HashMap<byte[], byte[]>();
                    keyAndValues.forEach((k, v) -> {
                        final byte[] keyAsBytes = mapKey(k);
                        final byte[] valueAsBytes = valueMapper.write(v);
                        keyAndValuesAsBytes.put(keyAsBytes, valueAsBytes);
                    });

                    if (expireAfterWriteMillis == null) {
                        redisClient.mset(keyAndValuesAsBytes).toCompletableFuture().join();
                    } else {
                        redisClient.psetex(keyAndValuesAsBytes, expireAfterWriteMillis).toCompletableFuture().join();
                    }
                    return keyAndValues;
                } catch (CompletionException e) {
                    observation.observeError(e.getCause());
                    return keyAndValues;
                } catch (Exception e) {
                    observation.observeError(e);
                    return keyAndValues;
                } finally {
                    observation.end();
                }
            });
    }

    @Override
    public V computeIfAbsent(@Nonnull K key, @Nonnull Function<K, V> mappingFunction) {
        if (key == null) {
            return null;
        }
        var observation = this.telemetry.observe("COMPUTE_IF_ABSENT");
        return ScopedValue
            .where(Observation.VALUE, observation)
            .where(OpentelemetryContext.VALUE, Context.current().with(observation.span()))
            .call(() -> {
                observation.observeKey(key);
                try {
                    try {
                        final byte[] keyAsBytes = mapKey(key);
                        final byte[] jsonAsBytes = (expireAfterAccessMillis == null)
                            ? redisClient.get(keyAsBytes).toCompletableFuture().join()
                            : redisClient.getex(keyAsBytes, expireAfterAccessMillis).toCompletableFuture().join();

                        var fromCache = valueMapper.read(jsonAsBytes);
                        if (fromCache != null) {
                            observation.observeValue(fromCache);
                            return fromCache;
                        }
                    } catch (Exception e) {
                        observation.observeError(e);
                    }
                    var value = mappingFunction.apply(key);
                    if (value == null) {
                        return null;
                    }
                    try {
                        final byte[] keyAsBytes = mapKey(key);
                        final byte[] valueAsBytes = valueMapper.write(value);
                        if (expireAfterWriteMillis == null) {
                            redisClient.set(keyAsBytes, valueAsBytes).toCompletableFuture().join();
                        } else {
                            redisClient.psetex(keyAsBytes, valueAsBytes, expireAfterWriteMillis).toCompletableFuture().join();
                        }
                    } catch (Exception e) {
                        observation.observeError(e);
                    }
                    return value;
                } catch (CompletionException e) {
                    observation.observeError(e.getCause());
                    return null;
                } catch (Exception e) {
                    observation.observeError(e);
                    return null;
                } finally {
                    observation.end();
                }
            });
    }

    @Nonnull
    @Override
    public Map<K, V> computeIfAbsent(@Nonnull Collection<K> keys, @Nonnull Function<Set<K>, Map<K, V>> mappingFunction) {
        if (keys == null || keys.isEmpty()) {
            return Collections.emptyMap();
        }
        var observation = this.telemetry.observe("COMPUTE_IF_ABSENT_MANY");
        return ScopedValue
            .where(Observation.VALUE, observation)
            .where(OpentelemetryContext.VALUE, Context.current().with(observation.span()))
            .call(() -> {
                final Map<K, V> fromCache = new HashMap<>();
                try {
                    observation.observeKeys(keys);
                    try {
                        final Map<K, byte[]> keysByKeyBytes = keys.stream()
                            .collect(Collectors.toMap(k -> k, this::mapKey, (v1, v2) -> v2));

                        final byte[][] keysByBytes = keysByKeyBytes.values().toArray(byte[][]::new);
                        final Map<byte[], byte[]> valueByKeys = (expireAfterAccessMillis == null)
                            ? redisClient.mget(keysByBytes).toCompletableFuture().join()
                            : redisClient.getex(keysByBytes, expireAfterAccessMillis).toCompletableFuture().join();

                        for (var entry : keysByKeyBytes.entrySet()) {
                            for (var e : valueByKeys.entrySet()) {
                                if (Arrays.equals(entry.getValue(), e.getKey())) {
                                    var value = valueMapper.read(e.getValue());
                                    fromCache.put(entry.getKey(), value);
                                }
                            }
                        }
                    } catch (Exception e) {
                        observation.observeError(e);
                    }
                    if (fromCache.size() == keys.size()) {
                        return fromCache;
                    }
                    var missingKeys = keys.stream()
                        .filter(k -> !fromCache.containsKey(k))
                        .collect(Collectors.toSet());
                    var values = mappingFunction.apply(missingKeys);
                    if (!values.isEmpty()) {
                        try {
                            var keyAndValuesAsBytes = new HashMap<byte[], byte[]>();
                            values.forEach((k, v) -> {
                                final byte[] keyAsBytes = mapKey(k);
                                final byte[] valueAsBytes = valueMapper.write(v);
                                keyAndValuesAsBytes.put(keyAsBytes, valueAsBytes);
                            });

                            if (expireAfterWriteMillis == null) {
                                redisClient.mset(keyAndValuesAsBytes).toCompletableFuture().join();
                            } else {
                                redisClient.psetex(keyAndValuesAsBytes, expireAfterWriteMillis).toCompletableFuture().join();
                            }
                        } catch (Exception e) {
                            observation.observeError(e);
                        }
                    }
                    fromCache.putAll(values);
                    observation.observeValues(fromCache);
                    return fromCache;
                } catch (CompletionException e) {
                    observation.observeError(e.getCause());
                    return fromCache;
                } catch (Exception e) {
                    observation.observeError(e);
                    return fromCache;
                } finally {
                    observation.end();
                }
            });
    }

    @Override
    public void invalidate(@Nonnull K key) {
        if (key == null) {
            return;
        }
        var observation = this.telemetry.observe("INVALIDATE");
        ScopedValue
            .where(Observation.VALUE, observation)
            .where(OpentelemetryContext.VALUE, Context.current().with(observation.span()))
            .run(() -> {
                observation.observeKey(key);
                try {
                    final byte[] keyAsBytes = mapKey(key);
                    redisClient.del(keyAsBytes).toCompletableFuture().join();
                } catch (CompletionException e) {
                    observation.observeError(e.getCause());
                } catch (Exception e) {
                    observation.observeError(e);
                } finally {
                    observation.end();
                }
            });
    }

    @Override
    public void invalidate(@Nonnull Collection<K> keys) {
        if (keys == null || keys.isEmpty()) {
            return;
        }
        var observation = this.telemetry.observe("INVALIDATE_MANY");
        ScopedValue
            .where(Observation.VALUE, observation)
            .where(OpentelemetryContext.VALUE, Context.current().with(observation.span()))
            .run(() -> {
                try {
                    observation.observeKeys(keys);
                    final byte[][] keysAsBytes = keys.stream()
                        .map(this::mapKey)
                        .toArray(byte[][]::new);

                    redisClient.del(keysAsBytes).toCompletableFuture().join();
                } catch (CompletionException e) {
                    observation.observeError(e.getCause());
                } catch (Exception e) {
                    observation.observeError(e);
                } finally {
                    observation.end();
                }
            });
    }

    @Override
    public void invalidateAll() {
        var observation = this.telemetry.observe("INVALIDATE_ALL");
        ScopedValue
            .where(Observation.VALUE, observation)
            .where(OpentelemetryContext.VALUE, Context.current().with(observation.span()))
            .run(() -> {
                try {
                    observation.observeKeys(List.of());
                    List<byte[]> keys = redisClient.scan(keyPrefix).toCompletableFuture().join();
                    if (keys.isEmpty()) {
                        return;
                    }

                    redisClient.del(keys).toCompletableFuture().join();
                } catch (CompletionException e) {
                    observation.observeError(e.getCause());
                } catch (Exception e) {
                    observation.observeError(e);
                } finally {
                    observation.end();
                }
            });
    }

    private byte[] mapKey(K key) {
        final byte[] suffixAsBytes = keyMapper.apply(key);
        if (this.keyPrefix == null) {
            return suffixAsBytes;
        } else {
            var keyAsBytes = new byte[keyPrefix.length + suffixAsBytes.length];
            System.arraycopy(this.keyPrefix, 0, keyAsBytes, 0, this.keyPrefix.length);
            System.arraycopy(suffixAsBytes, 0, keyAsBytes, this.keyPrefix.length, suffixAsBytes.length);

            return keyAsBytes;
        }
    }
}
