package ru.tinkoff.kora.cache.redis;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.cache.Cache;
import ru.tinkoff.kora.cache.redis.client.ReactiveRedisClient;
import ru.tinkoff.kora.cache.redis.client.SyncRedisClient;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public abstract class AbstractRedisCache<K, V> implements Cache<K, V> {

    private final String name;
    private final SyncRedisClient syncClient;
    private final ReactiveRedisClient reactiveClient;
    private final RedisCacheTelemetry telemetry;

    private final RedisCacheKeyMapper<K> keyMapper;
    private final RedisCacheValueMapper<V> valueMapper;

    private final Long expireAfterAccessMillis;
    private final Long expireAfterWriteMillis;

    protected AbstractRedisCache(String name,
                                 RedisCacheConfig config,
                                 SyncRedisClient syncClient,
                                 ReactiveRedisClient reactiveClient,
                                 RedisCacheTelemetry telemetry,
                                 RedisCacheKeyMapper<K> keyMapper,
                                 RedisCacheValueMapper<V> valueMapper) {
        this.name = name;
        this.syncClient = syncClient;
        this.reactiveClient = reactiveClient;
        this.telemetry = telemetry;
        this.keyMapper = keyMapper;
        this.valueMapper = valueMapper;
        this.expireAfterAccessMillis = (config.expireAfterAccess() == null)
            ? null
            : config.expireAfterAccess().toMillis();
        this.expireAfterWriteMillis = (config.expireAfterWrite() == null)
            ? null
            : config.expireAfterWrite().toMillis();
    }

    @Override
    public V get(@Nonnull K key) {
        if (key == null) {
            return null;
        }

        var telemetryContext = telemetry.create("GET", name);
        try {
            final byte[] keyAsBytes = keyMapper.apply(key);
            final byte[] jsonAsBytes = (expireAfterAccessMillis == null)
                ? syncClient.get(keyAsBytes)
                : syncClient.getExpire(keyAsBytes, expireAfterAccessMillis);

            final V value = valueMapper.read(jsonAsBytes);
            telemetryContext.recordSuccess(value);
            return value;
        } catch (Exception e) {
            telemetryContext.recordFailure(e);
            return null;
        }
    }

    @Nonnull
    @Override
    public Map<K, V> get(@Nonnull Collection<K> keys) {
        if (keys == null || keys.isEmpty()) {
            return null;
        }

        var telemetryContext = telemetry.create("GET_MANY", name);
        try {
            final Map<K, byte[]> keysByKeyBytes = keys.stream()
                .collect(Collectors.toMap(k -> k, keyMapper, (v1, v2) -> v2));

            final byte[][] keysByBytes = keysByKeyBytes.values().toArray(byte[][]::new);
            final Map<byte[], byte[]> valueByKeys = (expireAfterAccessMillis == null)
                ? syncClient.get(keysByBytes)
                : syncClient.getExpire(keysByBytes, expireAfterAccessMillis);

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

        var telemetryContext = telemetry.create("PUT", name);

        try {
            final byte[] keyAsBytes = keyMapper.apply(key);
            final byte[] valueAsBytes = valueMapper.write(value);
            if (expireAfterWriteMillis == null) {
                syncClient.set(keyAsBytes, valueAsBytes);
            } else {
                syncClient.setExpire(keyAsBytes, valueAsBytes, expireAfterWriteMillis);
            }
            telemetryContext.recordSuccess();
            return value;
        } catch (Exception e) {
            telemetryContext.recordFailure(e);
            return value;
        }
    }

    @Override
    public V computeIfAbsent(@Nonnull K key, @Nonnull Function<K, V> mappingFunction) {
        var fromCache = get(key);
        if (fromCache != null) {
            return fromCache;
        }

        var value = mappingFunction.apply(key);
        if (value != null) {
            put(key, value);
        }

        return value;
    }

    @Nonnull
    @Override
    public Map<K, V> computeIfAbsent(@Nonnull Collection<K> keys, @Nonnull Function<Set<K>, Map<K, V>> mappingFunction) {
        var fromCache = get(keys);
        if (fromCache.size() == keys.size()) {
            return fromCache;
        }

        var missingKeys = keys.stream()
            .filter(k -> !fromCache.containsKey(k))
            .collect(Collectors.toSet());

        var values = mappingFunction.apply(missingKeys);
        if (values != null) {
            values.forEach(this::put);
        }

        var result = new HashMap<>(fromCache);
        result.putAll(values);
        return result;
    }

    @Override
    public void invalidate(@Nonnull K key) {
        if (key != null) {
            final byte[] keyAsBytes = keyMapper.apply(key);
            var telemetryContext = telemetry.create("INVALIDATE", name);

            try {
                syncClient.del(keyAsBytes);
                telemetryContext.recordSuccess();
            } catch (Exception e) {
                telemetryContext.recordFailure(e);
            }
        }
    }

    @Override
    public void invalidate(@Nonnull Collection<K> keys) {
        if (keys != null && !keys.isEmpty()) {
            var telemetryContext = telemetry.create("INVALIDATE_MANY", name);

            try {
                final byte[][] keysAsBytes = keys.stream()
                    .map(keyMapper)
                    .toArray(byte[][]::new);

                syncClient.del(keysAsBytes);
                telemetryContext.recordSuccess();
            } catch (Exception e) {
                telemetryContext.recordFailure(e);
            }
        }
    }

    @Override
    public void invalidateAll() {
        var telemetryContext = telemetry.create("INVALIDATE_ALL", name);

        try {
            syncClient.flushAll();
            telemetryContext.recordSuccess();
        } catch (Exception e) {
            telemetryContext.recordFailure(e);
        }
    }

    @Nonnull
    @Override
    public Mono<V> getAsync(@Nonnull K key) {
        if (key == null) {
            return Mono.empty();
        }

        return Mono.defer(() -> {
            var telemetryContext = telemetry.create("GET", name);
            final byte[] keyAsBytes = keyMapper.apply(key);

            Mono<byte[]> responseMono = (expireAfterAccessMillis == null)
                ? reactiveClient.get(keyAsBytes)
                : reactiveClient.getExpire(keyAsBytes, expireAfterAccessMillis);

            return responseMono.map(jsonAsBytes -> {
                    final V value = valueMapper.read(jsonAsBytes);
                    telemetryContext.recordSuccess(value);
                    return value;
                })
                .onErrorResume(e -> {
                    telemetryContext.recordFailure(e);
                    return Mono.empty();
                });
        });
    }

    @Nonnull
    @Override
    public Mono<Map<K, V>> getAsync(@Nonnull Collection<K> keys) {
        if (keys == null || keys.isEmpty()) {
            return Mono.just(Collections.emptyMap());
        }

        return Mono.defer(() -> {
            var telemetryContext = telemetry.create("GET_MANY", name);
            var keysByKeyByte = keys.stream()
                .collect(Collectors.toMap(k -> k, keyMapper, (v1, v2) -> v2));

            var keysAsBytes = keysByKeyByte.values().toArray(byte[][]::new);
            var responseMono = (expireAfterAccessMillis == null)
                ? reactiveClient.get(keysAsBytes)
                : reactiveClient.getExpire(keysAsBytes, expireAfterAccessMillis);

            return responseMono.map(valuesByKeys -> {
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
                .onErrorResume(e -> {
                    telemetryContext.recordFailure(e);
                    return Mono.just(Collections.emptyMap());
                });
        });
    }

    @Nonnull
    @Override
    public Mono<V> putAsync(@Nonnull K key, @Nonnull V value) {
        if (key == null) {
            return Mono.justOrEmpty(value);
        }

        return Mono.defer(() -> {
            var telemetryContext = telemetry.create("PUT", name);
            final byte[] keyAsBytes = keyMapper.apply(key);
            final byte[] valueAsBytes = valueMapper.write(value);
            final Mono<Boolean> responseMono = (expireAfterWriteMillis == null)
                ? reactiveClient.set(keyAsBytes, valueAsBytes)
                : reactiveClient.setExpire(keyAsBytes, valueAsBytes, expireAfterWriteMillis);

            return responseMono.map(r -> value)
                .switchIfEmpty(Mono.fromCallable(() -> {
                    telemetryContext.recordSuccess();
                    return value;
                }))
                .onErrorResume(e -> {
                    telemetryContext.recordFailure(e);
                    return Mono.just(value);
                });
        });
    }

    @Override
    public Mono<V> computeIfAbsentAsync(@Nonnull K key, @Nonnull Function<K, Mono<V>> mappingFunction) {
        return getAsync(key)
            .switchIfEmpty(mappingFunction.apply(key)
                .flatMap(value -> putAsync(key, value).thenReturn(value)));
    }

    @Nonnull
    @Override
    public Mono<Map<K, V>> computeIfAbsentAsync(@Nonnull Collection<K> keys, @Nonnull Function<Set<K>, Mono<Map<K, V>>> mappingFunction) {
        return getAsync(keys)
            .flatMap(fromCache -> {
                if (fromCache.size() == keys.size()) {
                    return Mono.just(fromCache);
                }

                var missingKeys = keys.stream()
                    .filter(k -> !fromCache.containsKey(k))
                    .collect(Collectors.toSet());

                return mappingFunction.apply(missingKeys)
                    .flatMap(loaded -> {
                        var putMonos = loaded.entrySet().stream()
                            .map(e -> putAsync(e.getKey(), e.getValue()))
                            .toList();

                        final Map<K, V> result;
                        if (fromCache.isEmpty()) {
                            result = loaded;
                        } else {
                            result = new HashMap<>(fromCache);
                            result.putAll(loaded);
                        }

                        return Flux.merge(putMonos).then(Mono.just(result));
                    });
            });
    }

    @Nonnull
    @Override
    public Mono<Boolean> invalidateAsync(@Nonnull K key) {
        if (key == null) {
            return Mono.just(false);
        }

        return Mono.defer(() -> {
            var telemetryContext = telemetry.create("INVALIDATE", name);
            final byte[] keyAsBytes = keyMapper.apply(key);
            return reactiveClient.del(keyAsBytes)
                .then(Mono.just(true))
                .doOnSuccess(r -> telemetryContext.recordSuccess())
                .onErrorResume(e -> {
                    telemetryContext.recordFailure(e);
                    return Mono.just(false);
                });
        });
    }

    @Override
    public Mono<Boolean> invalidateAsync(@Nonnull Collection<K> keys) {
        if (keys == null || keys.isEmpty()) {
            return Mono.just(false);
        }

        return Mono.defer(() -> {
            var telemetryContext = telemetry.create("INVALIDATE_MANY", name);
            final byte[][] keyAsBytes = keys.stream()
                .distinct()
                .map(keyMapper)
                .toArray(byte[][]::new);

            return reactiveClient.del(keyAsBytes)
                .then(Mono.just(true))
                .doOnSuccess(r -> telemetryContext.recordSuccess())
                .onErrorResume(e -> {
                    telemetryContext.recordFailure(e);
                    return Mono.just(false);
                });
        });
    }

    @Nonnull
    @Override
    public Mono<Boolean> invalidateAllAsync() {
        return Mono.defer(() -> {
            var telemetryContext = telemetry.create("INVALIDATE_ALL", name);
            return reactiveClient.flushAll()
                .then(Mono.just(true))
                .doOnSuccess(r -> telemetryContext.recordSuccess())
                .onErrorResume(e -> {
                    telemetryContext.recordFailure(e);
                    return Mono.just(false);
                });
        });
    }
}
