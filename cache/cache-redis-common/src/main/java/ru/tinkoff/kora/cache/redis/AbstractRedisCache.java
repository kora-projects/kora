package ru.tinkoff.kora.cache.redis;

import jakarta.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.cache.AsyncCache;
import ru.tinkoff.kora.cache.telemetry.CacheTelemetry;
import ru.tinkoff.kora.cache.telemetry.CacheTelemetryArgs;
import ru.tinkoff.kora.cache.telemetry.CacheTelemetryFactory;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.stream.Collectors;

public abstract class AbstractRedisCache<K, V> implements AsyncCache<K, V> {

    private static final Logger logger = LoggerFactory.getLogger(RedisCache.class);

    private static final String ORIGIN = "redis";

    private final RedisCacheClient redisClient;
    private final RedisCacheAsyncClient redisAsyncClient;
    private final CacheTelemetry telemetry;
    private final byte[] keyPrefix;

    private final RedisCacheKeyMapper<K> keyMapper;
    private final RedisCacheValueMapper<V> valueMapper;

    private final Long expireAfterAccessMillis;
    private final Long expireAfterWriteMillis;

    protected AbstractRedisCache(String name,
                                 RedisCacheConfig config,
                                 RedisCacheClient redisClient,
                                 RedisCacheAsyncClient redisAsyncClient,
                                 CacheTelemetryFactory telemetryFactory,
                                 RedisCacheKeyMapper<K> keyMapper,
                                 RedisCacheValueMapper<V> valueMapper) {
        this.redisClient = redisClient;
        this.redisAsyncClient = redisAsyncClient;
        this.telemetry = telemetryFactory.get(config.telemetry(), new CacheTelemetryArgs() {
            @Nonnull
            @Override
            public String cacheName() {
                return name;
            }

            @Nonnull
            @Override
            public String origin() {
                return ORIGIN;
            }
        });
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

        var telemetryContext = telemetry.get("GET");
        try {
            final byte[] keyAsBytes = mapKey(key);
            final byte[] jsonAsBytes = (expireAfterAccessMillis == null)
                ? redisClient.get(keyAsBytes)
                : redisClient.getex(keyAsBytes, expireAfterAccessMillis);

            final V value = valueMapper.read(jsonAsBytes);
            telemetryContext.recordSuccess(value);
            return value;
        } catch (CompletionException e) {
            telemetryContext.recordFailure(e.getCause());
            return null;
        } catch (Exception e) {
            telemetryContext.recordFailure(e);
            return null;
        }
    }

    @Nonnull
    @Override
    public Map<K, V> get(@Nonnull Collection<K> keys) {
        if (keys == null || keys.isEmpty()) {
            return Collections.emptyMap();
        }

        var telemetryContext = telemetry.get("GET_MANY");
        try {
            final Map<K, byte[]> keysByKeyBytes = keys.stream()
                .collect(Collectors.toMap(k -> k, this::mapKey, (v1, v2) -> v2));

            final byte[][] keysByBytes = keysByKeyBytes.values().toArray(byte[][]::new);
            final Map<byte[], byte[]> valueByKeys = (expireAfterAccessMillis == null)
                ? redisClient.mget(keysByBytes)
                : redisClient.getex(keysByBytes, expireAfterAccessMillis);

            final Map<K, V> keyToValue = new HashMap<>();
            for (var entry : keysByKeyBytes.entrySet()) {
                valueByKeys.forEach((k, v) -> {
                    if (Arrays.equals(entry.getValue(), k)) {
                        var value = valueMapper.read(v);
                        keyToValue.put(entry.getKey(), value);
                    }
                });
            }

            telemetryContext.recordSuccess(keyToValue);
            return keyToValue;
        } catch (CompletionException e) {
            telemetryContext.recordFailure(e.getCause());
            return Collections.emptyMap();
        } catch (Exception e) {
            telemetryContext.recordFailure(e);
            return Collections.emptyMap();
        }
    }

    @Nonnull
    @Override
    public V put(@Nonnull K key, @Nonnull V value) {
        if (key == null || value == null) {
            return null;
        }

        var telemetryContext = telemetry.get("PUT");

        try {
            final byte[] keyAsBytes = mapKey(key);
            final byte[] valueAsBytes = valueMapper.write(value);
            if (expireAfterWriteMillis == null) {
                redisClient.set(keyAsBytes, valueAsBytes);
            } else {
                redisClient.psetex(keyAsBytes, valueAsBytes, expireAfterWriteMillis);
            }
            telemetryContext.recordSuccess(null);
            return value;
        } catch (CompletionException e) {
            telemetryContext.recordFailure(e.getCause());
            return value;
        } catch (Exception e) {
            telemetryContext.recordFailure(e);
            return value;
        }
    }

    @Nonnull
    @Override
    public Map<K, V> put(@Nonnull Map<K, V> keyAndValues) {
        if (keyAndValues == null || keyAndValues.isEmpty()) {
            return Collections.emptyMap();
        }

        var telemetryContext = telemetry.get("PUT_MANY");

        try {
            var keyAndValuesAsBytes = new HashMap<byte[], byte[]>();
            keyAndValues.forEach((k, v) -> {
                final byte[] keyAsBytes = mapKey(k);
                final byte[] valueAsBytes = valueMapper.write(v);
                keyAndValuesAsBytes.put(keyAsBytes, valueAsBytes);
            });

            if (expireAfterWriteMillis == null) {
                redisClient.mset(keyAndValuesAsBytes);
            } else {
                redisClient.psetex(keyAndValuesAsBytes, expireAfterWriteMillis);
            }

            telemetryContext.recordSuccess(null);
            return keyAndValues;
        } catch (CompletionException e) {
            telemetryContext.recordFailure(e.getCause());
            return keyAndValues;
        } catch (Exception e) {
            telemetryContext.recordFailure(e);
            return keyAndValues;
        }
    }

    @Override
    public V computeIfAbsent(@Nonnull K key, @Nonnull Function<K, V> mappingFunction) {
        if (key == null) {
            return null;
        }

        var telemetryContext = telemetry.get("COMPUTE_IF_ABSENT");

        V fromCache = null;
        try {
            final byte[] keyAsBytes = mapKey(key);
            final byte[] jsonAsBytes = (expireAfterAccessMillis == null)
                ? redisClient.get(keyAsBytes)
                : redisClient.getex(keyAsBytes, expireAfterAccessMillis);

            fromCache = valueMapper.read(jsonAsBytes);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }

        if (fromCache != null) {
            telemetryContext.recordSuccess(null);
            return fromCache;
        }

        try {
            var value = mappingFunction.apply(key);
            if (value != null) {
                try {
                    final byte[] keyAsBytes = mapKey(key);
                    final byte[] valueAsBytes = valueMapper.write(value);
                    if (expireAfterWriteMillis == null) {
                        redisClient.set(keyAsBytes, valueAsBytes);
                    } else {
                        redisClient.psetex(keyAsBytes, valueAsBytes, expireAfterWriteMillis);
                    }
                } catch (Exception e) {
                    logger.error(e.getMessage(), e);
                }
            }

            telemetryContext.recordSuccess(null);
            return value;
        } catch (CompletionException e) {
            telemetryContext.recordFailure(e.getCause());
            return null;
        } catch (Exception e) {
            telemetryContext.recordFailure(e);
            return null;
        }
    }

    @Nonnull
    @Override
    public Map<K, V> computeIfAbsent(@Nonnull Collection<K> keys, @Nonnull Function<Set<K>, Map<K, V>> mappingFunction) {
        if (keys == null || keys.isEmpty()) {
            return Collections.emptyMap();
        }

        var telemetryContext = telemetry.get("COMPUTE_IF_ABSENT_MANY");

        final Map<K, V> fromCache = new HashMap<>();
        try {
            final Map<K, byte[]> keysByKeyBytes = keys.stream()
                .collect(Collectors.toMap(k -> k, this::mapKey, (v1, v2) -> v2));

            final byte[][] keysByBytes = keysByKeyBytes.values().toArray(byte[][]::new);
            final Map<byte[], byte[]> valueByKeys = (expireAfterAccessMillis == null)
                ? redisClient.mget(keysByBytes)
                : redisClient.getex(keysByBytes, expireAfterAccessMillis);

            for (var entry : keysByKeyBytes.entrySet()) {
                valueByKeys.forEach((k, v) -> {
                    if (Arrays.equals(entry.getValue(), k)) {
                        var value = valueMapper.read(v);
                        fromCache.put(entry.getKey(), value);
                    }
                });
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }

        if (fromCache.size() == keys.size()) {
            telemetryContext.recordSuccess(null);
            return fromCache;
        }

        var missingKeys = keys.stream()
            .filter(k -> !fromCache.containsKey(k))
            .collect(Collectors.toSet());

        try {
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
                        redisClient.mset(keyAndValuesAsBytes);
                    } else {
                        redisClient.psetex(keyAndValuesAsBytes, expireAfterWriteMillis);
                    }
                } catch (Exception e) {
                    logger.error(e.getMessage(), e);
                }
            }

            telemetryContext.recordSuccess(null);
            fromCache.putAll(values);
            return fromCache;
        } catch (CompletionException e) {
            telemetryContext.recordFailure(e.getCause());
            return fromCache;
        } catch (Exception e) {
            telemetryContext.recordFailure(e);
            return fromCache;
        }
    }

    @Override
    public void invalidate(@Nonnull K key) {
        if (key != null) {
            final byte[] keyAsBytes = mapKey(key);
            var telemetryContext = telemetry.get("INVALIDATE");

            try {
                redisClient.del(keyAsBytes);
                telemetryContext.recordSuccess(null);
            } catch (CompletionException e) {
                telemetryContext.recordFailure(e.getCause());
            } catch (Exception e) {
                telemetryContext.recordFailure(e);
            }
        }
    }

    @Override
    public void invalidate(@Nonnull Collection<K> keys) {
        if (keys != null && !keys.isEmpty()) {
            var telemetryContext = telemetry.get("INVALIDATE_MANY");

            try {
                final byte[][] keysAsBytes = keys.stream()
                    .map(this::mapKey)
                    .toArray(byte[][]::new);

                redisClient.del(keysAsBytes);
                telemetryContext.recordSuccess(null);
            } catch (CompletionException e) {
                telemetryContext.recordFailure(e.getCause());
            } catch (Exception e) {
                telemetryContext.recordFailure(e);
            }
        }
    }

    @Override
    public void invalidateAll() {
        var telemetryContext = telemetry.get("INVALIDATE_ALL");

        try {
            redisClient.flushAll();
            telemetryContext.recordSuccess(null);
        } catch (CompletionException e) {
            telemetryContext.recordFailure(e.getCause());
        } catch (Exception e) {
            telemetryContext.recordFailure(e);
        }
    }

    @Nonnull
    @Override
    public CompletionStage<V> getAsync(@Nonnull K key) {
        if (key == null) {
            return CompletableFuture.completedFuture(null);
        }

        var telemetryContext = telemetry.get("GET");
        final byte[] keyAsBytes = mapKey(key);

        CompletionStage<byte[]> responseCompletionStage = (expireAfterAccessMillis == null)
            ? redisAsyncClient.get(keyAsBytes)
            : redisAsyncClient.getex(keyAsBytes, expireAfterAccessMillis);

        return responseCompletionStage
            .thenApply(jsonAsBytes -> {
                final V value = valueMapper.read(jsonAsBytes);
                telemetryContext.recordSuccess(value);
                return value;
            })
            .exceptionally(e -> {
                telemetryContext.recordFailure(e);
                return null;
            });
    }

    @Nonnull
    @Override
    public CompletionStage<Map<K, V>> getAsync(@Nonnull Collection<K> keys) {
        if (keys == null || keys.isEmpty()) {
            return CompletableFuture.completedFuture(Collections.emptyMap());
        }

        var telemetryContext = telemetry.get("GET_MANY");
        var keysByKeyByte = keys.stream()
            .collect(Collectors.toMap(k -> k, this::mapKey, (v1, v2) -> v2));

        var keysAsBytes = keysByKeyByte.values().toArray(byte[][]::new);
        var responseCompletionStage = (expireAfterAccessMillis == null)
            ? redisAsyncClient.mget(keysAsBytes)
            : redisAsyncClient.getex(keysAsBytes, expireAfterAccessMillis);

        return responseCompletionStage
            .thenApply(valuesByKeys -> {
                final Map<K, V> keyToValue = new HashMap<>();
                for (var entry : keysByKeyByte.entrySet()) {
                    valuesByKeys.forEach((k, v) -> {
                        if (Arrays.equals(entry.getValue(), k)) {
                            var value = valueMapper.read(v);
                            keyToValue.put(entry.getKey(), value);
                        }
                    });
                }
                telemetryContext.recordSuccess(keyToValue);
                return keyToValue;
            })
            .exceptionally(e -> {
                telemetryContext.recordFailure(e);
                return Collections.emptyMap();
            });
    }

    @Nonnull
    @Override
    public CompletionStage<V> putAsync(@Nonnull K key, @Nonnull V value) {
        if (key == null) {
            return CompletableFuture.completedFuture(value);
        }

        var telemetryContext = telemetry.get("PUT");
        final byte[] keyAsBytes = mapKey(key);
        final byte[] valueAsBytes = valueMapper.write(value);
        var responseCompletionStage = (expireAfterWriteMillis == null)
            ? redisAsyncClient.set(keyAsBytes, valueAsBytes)
            : redisAsyncClient.psetex(keyAsBytes, valueAsBytes, expireAfterWriteMillis);

        return responseCompletionStage
            .thenApply(r -> {
                telemetryContext.recordSuccess(null);
                return value;
            })
            .exceptionally(e -> {
                telemetryContext.recordFailure(e);
                return value;
            });
    }

    @Nonnull
    @Override
    public CompletionStage<Map<K, V>> putAsync(@Nonnull Map<K, V> keyAndValues) {
        if (keyAndValues == null || keyAndValues.isEmpty()) {
            return CompletableFuture.completedFuture(Collections.emptyMap());
        }

        var telemetryContext = telemetry.get("PUT_MANY");
        var keyAndValuesAsBytes = new HashMap<byte[], byte[]>();
        keyAndValues.forEach((k, v) -> {
            final byte[] keyAsBytes = mapKey(k);
            final byte[] valueAsBytes = valueMapper.write(v);
            keyAndValuesAsBytes.put(keyAsBytes, valueAsBytes);
        });

        var responseCompletionStage = (expireAfterWriteMillis == null)
            ? redisAsyncClient.mset(keyAndValuesAsBytes)
            : redisAsyncClient.psetex(keyAndValuesAsBytes, expireAfterAccessMillis);

        return responseCompletionStage
            .thenApply(r -> {
                telemetryContext.recordSuccess(null);
                return keyAndValues;
            })
            .exceptionally(e -> {
                telemetryContext.recordFailure(e);
                return keyAndValues;
            });
    }

    @Override
    public CompletionStage<V> computeIfAbsentAsync(@Nonnull K key, @Nonnull Function<K, CompletionStage<V>> mappingFunction) {
        if (key == null) {
            return CompletableFuture.completedFuture(null);
        }

        var telemetryContext = telemetry.get("COMPUTE_IF_ABSENT");
        final byte[] keyAsBytes = mapKey(key);
        final CompletionStage<byte[]> responseCompletionStage = (expireAfterAccessMillis == null)
            ? redisAsyncClient.get(keyAsBytes)
            : redisAsyncClient.getex(keyAsBytes, expireAfterAccessMillis);

        return responseCompletionStage
            .thenApply(valueMapper::read)
            .thenCompose(fromCache -> {
                if (fromCache != null) {
                    return CompletableFuture.completedFuture(fromCache);
                }

                return mappingFunction.apply(key)
                    .thenCompose(value -> {
                        if (value == null) {
                            return CompletableFuture.completedFuture(null);
                        }

                        final byte[] valueAsBytes = valueMapper.write(value);
                        var putFutureResponse = (expireAfterWriteMillis == null)
                            ? redisAsyncClient.set(keyAsBytes, valueAsBytes)
                            : redisAsyncClient.psetex(keyAsBytes, valueAsBytes, expireAfterWriteMillis);

                        return putFutureResponse
                            .thenApply(v -> {
                                telemetryContext.recordSuccess(null);
                                return value;
                            });
                    });
            })
            .exceptionally(e -> {
                telemetryContext.recordFailure(e);
                return null;
            });
    }

    @Nonnull
    @Override
    public CompletionStage<Map<K, V>> computeIfAbsentAsync(@Nonnull Collection<K> keys, @Nonnull Function<Set<K>, CompletionStage<Map<K, V>>> mappingFunction) {
        if (keys == null || keys.isEmpty()) {
            return CompletableFuture.completedFuture(Collections.emptyMap());
        }

        var telemetryContext = telemetry.get("COMPUTE_IF_ABSENT_MANY");
        final Map<K, byte[]> keysByKeyBytes = keys.stream()
            .collect(Collectors.toMap(k -> k, this::mapKey, (v1, v2) -> v2));

        final byte[][] keysByBytes = keysByKeyBytes.values().toArray(byte[][]::new);
        var responseCompletionStage = (expireAfterAccessMillis == null)
            ? redisAsyncClient.mget(keysByBytes)
            : redisAsyncClient.getex(keysByBytes, expireAfterAccessMillis);

        return responseCompletionStage
            .thenApply(valueByKeys -> {
                final Map<K, V> fromCache = new HashMap<>();
                for (var entry : keysByKeyBytes.entrySet()) {
                    valueByKeys.forEach((k, v) -> {
                        if (Arrays.equals(entry.getValue(), k)) {
                            var value = valueMapper.read(v);
                            fromCache.put(entry.getKey(), value);
                        }
                    });
                }

                return fromCache;
            })
            .thenCompose(fromCache -> {
                if (fromCache.size() == keys.size()) {
                    return CompletableFuture.completedFuture(fromCache);
                }

                var missingKeys = keys.stream()
                    .filter(k -> !fromCache.containsKey(k))
                    .collect(Collectors.toSet());

                return mappingFunction.apply(missingKeys)
                    .thenCompose(values -> {
                        if (values.isEmpty()) {
                            return CompletableFuture.completedFuture(fromCache);
                        }

                        var keyAndValuesAsBytes = new HashMap<byte[], byte[]>();
                        values.forEach((k, v) -> {
                            final byte[] keyAsBytes = mapKey(k);
                            final byte[] valueAsBytes = valueMapper.write(v);
                            keyAndValuesAsBytes.put(keyAsBytes, valueAsBytes);
                        });

                        var putCompletionStage = (expireAfterAccessMillis == null)
                            ? redisAsyncClient.mset(keyAndValuesAsBytes)
                            : redisAsyncClient.psetex(keyAndValuesAsBytes, expireAfterAccessMillis);

                        return putCompletionStage
                            .thenApply(v -> {
                                telemetryContext.recordSuccess(null);
                                fromCache.putAll(values);
                                return fromCache;
                            });
                    });
            })
            .exceptionally(e -> {
                telemetryContext.recordFailure(e);
                return Collections.emptyMap();
            });
    }

    @Nonnull
    @Override
    public CompletionStage<Boolean> invalidateAsync(@Nonnull K key) {
        if (key == null) {
            return CompletableFuture.completedFuture(false);
        }

        var telemetryContext = telemetry.get("INVALIDATE");
        final byte[] keyAsBytes = mapKey(key);
        return redisAsyncClient.del(keyAsBytes)
            .thenApply(r -> {
                telemetryContext.recordSuccess(null);
                return true;
            })
            .exceptionally(e -> {
                telemetryContext.recordFailure(e);
                return false;
            });
    }

    @Override
    public CompletionStage<Boolean> invalidateAsync(@Nonnull Collection<K> keys) {
        if (keys == null || keys.isEmpty()) {
            return CompletableFuture.completedFuture(false);
        }

        var telemetryContext = telemetry.get("INVALIDATE_MANY");
        final byte[][] keyAsBytes = keys.stream()
            .distinct()
            .map(this::mapKey)
            .toArray(byte[][]::new);

        return redisAsyncClient.del(keyAsBytes)
            .thenApply(r -> {
                telemetryContext.recordSuccess(null);
                return true;
            })
            .exceptionally(e -> {
                telemetryContext.recordFailure(e);
                return false;
            });
    }

    @Nonnull
    @Override
    public CompletionStage<Boolean> invalidateAllAsync() {
        var telemetryContext = telemetry.get("INVALIDATE_ALL");
        return redisAsyncClient.flushAll()
            .thenApply(r -> {
                telemetryContext.recordSuccess(null);
                return true;
            })
            .exceptionally(e -> {
                telemetryContext.recordFailure(e);
                return false;
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
