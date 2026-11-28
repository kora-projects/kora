package io.koraframework.cache.redis;

import io.koraframework.cache.redis.mapper.*;
import io.koraframework.cache.redis.telemetry.*;
import io.koraframework.cache.redis.telemetry.RedisCacheTelemetry.*;
import io.koraframework.common.context.*;
import io.koraframework.common.telemetry.*;
import io.opentelemetry.context.*;
import org.jspecify.annotations.*;
import org.slf4j.*;
import org.slf4j.helpers.*;

import java.nio.charset.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import java.util.stream.*;

public abstract class AbstractRedisCache<K, V> implements RedisCache<K, V> {

    private final Logger logger;

    private final RedisCacheClient redisClient;
    private final RedisCacheTelemetry telemetry;
    private final boolean enabled;

    private final RedisCacheKeyMapper<K> keyMapper;
    private final RedisCacheValueMapper<V> valueMapper;

    private final byte @Nullable [] keyPrefix;
    @Nullable
    private final Long expireAfterAccessMillis;
    @Nullable
    private final Long expireAfterWriteMillis;

    protected AbstractRedisCache(String cacheConfigPath,
                                 RedisCacheConfig config,
                                 RedisCacheClient redisClient,
                                 RedisCacheTelemetryFactory telemetryFactory,
                                 RedisCacheKeyMapper<K> keyMapper,
                                 RedisCacheValueMapper<V> valueMapper) {
        this.redisClient = redisClient;
        this.telemetry = telemetryFactory.get(cacheConfigPath, getClass(), config.telemetry());
        this.enabled = config.enabled();
        this.keyMapper = keyMapper;
        this.valueMapper = valueMapper;
        this.expireAfterAccessMillis = (config.expireAfterAccess() == null) ? null : config.expireAfterAccess().toMillis();
        this.expireAfterWriteMillis = (config.expireAfterWrite() == null) ? null : config.expireAfterWrite().toMillis();

        this.logger = config.telemetry().logging().enabled()
            ? LoggerFactory.getLogger(getClass())
            : NOPLogger.NOP_LOGGER;

        if (config.keyPrefix().isBlank()) {
            this.keyPrefix = null;
            logger.warn("Redis Cache key prefix is empty! This can lead to key collisions or flushAll keys for invalidateAll command.");
        } else {
            var prefixRaw = config.keyPrefix().getBytes(StandardCharsets.UTF_8);
            this.keyPrefix = new byte[prefixRaw.length + RedisCacheKeyMapper.DELIMITER.length];
            System.arraycopy(prefixRaw, 0, this.keyPrefix, 0, prefixRaw.length);
            System.arraycopy(RedisCacheKeyMapper.DELIMITER, 0, this.keyPrefix, prefixRaw.length, RedisCacheKeyMapper.DELIMITER.length);
        }
    }

    @Override
    public V get(K key) {
        if (key == null || !enabled) {
            return null;
        }

        var observation = this.telemetry.observe(Operation.GET);
        var current = KoraContext.current();
        var context = (current != null ? current : KoraContext.empty())
            .with(OpentelemetryContextStorage.OPENTELEMETRY_KEY, Context.current().with(observation.span()));

        return Observation.attach(context, observation).call(() -> {
            observation.observeKey(key);
            try {
                final byte[] keyAsBytes = mapKey(key);
                final byte[] jsonAsBytes = (expireAfterAccessMillis == null)
                    ? redisClient.get(keyAsBytes)
                    : redisClient.getex(keyAsBytes, expireAfterAccessMillis);

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

    @Override
    public Map<K, V> get(Collection<K> keys) {
        if (keys == null || keys.isEmpty() || !enabled) {
            return Collections.emptyMap();
        }

        var observation = this.telemetry.observe(Operation.GET_MANY);
        var current = KoraContext.current();
        var context = (current != null ? current : KoraContext.empty())
            .with(OpentelemetryContextStorage.OPENTELEMETRY_KEY, Context.current().with(observation.span()));

        return Observation.attach(context, observation).call(() -> {
            try {
                observation.observeKeys(keys);
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

                observation.observeValues(keyToValue);
                return keyToValue;
            } catch (CompletionException e) {
                observation.observeError(e.getCause());
                return Collections.emptyMap();
            } catch (Exception e) {
                observation.observeError(e);
                return Collections.emptyMap();
            } finally {
                observation.end();
            }
        });
    }

    @Override
    public V put(K key, V value) {
        return putExpireAfterWrite(key, value, expireAfterWriteMillis);
    }

    @Override
    public V putExpireAfterWrite(K key, V value, Duration expireAfterWrite) {
        Objects.requireNonNull(expireAfterWrite, "RedisCache#putExpireAfterWrite received nullable expireAfterWrite argument");
        return putExpireAfterWrite(key, value, expireAfterWrite.toMillis());
    }

    private V putExpireAfterWrite(K key, V value, @Nullable Long expireAfterWriteMillis) {
        if (key == null || value == null) {
            return null;
        }
        if (!enabled) {
            return value;
        }

        var observation = this.telemetry.observe(Operation.PUT);
        var current = KoraContext.current();
        var context = (current != null ? current : KoraContext.empty())
            .with(OpentelemetryContextStorage.OPENTELEMETRY_KEY, Context.current().with(observation.span()));

        return Observation.attach(context, observation).call(() -> {
            observation.observeKey(key);
            observation.observeValue(value);
            try {
                final byte[] keyAsBytes = mapKey(key);
                final byte[] valueAsBytes = valueMapper.write(value);
                if (expireAfterWriteMillis == null) {
                    redisClient.set(keyAsBytes, valueAsBytes);
                } else {
                    redisClient.psetex(keyAsBytes, valueAsBytes, expireAfterWriteMillis);
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

    @Override
    public Map<K, V> put(Map<K, V> keyAndValues) {
        return putExpireAfterWrite(keyAndValues, expireAfterWriteMillis);
    }

    @Override
    public Map<K, V> putExpireAfterWrite(Map<K, V> keyAndValues, Duration expireAfterWrite) {
        Objects.requireNonNull(expireAfterWrite, "RedisCache#putExpireAfterWrite received nullable expireAfterWrite argument");
        return putExpireAfterWrite(keyAndValues, expireAfterWrite.toMillis());
    }

    private Map<K, V> putExpireAfterWrite(Map<K, V> keyAndValues, @Nullable Long expireAfterWriteMillis) {
        if (keyAndValues == null || keyAndValues.isEmpty()) {
            return Collections.emptyMap();
        }
        if (!enabled) {
            return keyAndValues;
        }

        var observation = this.telemetry.observe(Operation.PUT_MANY);
        var current = KoraContext.current();
        var context = (current != null ? current : KoraContext.empty())
            .with(OpentelemetryContextStorage.OPENTELEMETRY_KEY, Context.current().with(observation.span()));

        return Observation.attach(context, observation).call(() -> {
            observation.observeKeys(keyAndValues.keySet());
            observation.observeValues(keyAndValues);
            try {
                var keyAndValuesAsBytes = new HashMap<byte[], byte[]>();
                keyAndValues.forEach((k, v) -> {
                    if (k != null && v != null) {
                        final byte[] keyAsBytes = mapKey(k);
                        final byte[] valueAsBytes = valueMapper.write(v);
                        keyAndValuesAsBytes.put(keyAsBytes, valueAsBytes);
                    } else {
                        logger.warn("Key or value is null for 'PUT_MANY' operation, skipping...");
                    }
                });

                if (keyAndValuesAsBytes.isEmpty()) {
                    return Collections.emptyMap();
                }

                if (expireAfterWriteMillis == null) {
                    redisClient.mset(keyAndValuesAsBytes);
                } else {
                    redisClient.psetex(keyAndValuesAsBytes, expireAfterWriteMillis);
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
    public V computeIfAbsent(K key, Function<K, @Nullable V> mappingFunction) {
        if (key == null || !enabled) {
            return mappingFunction.apply(key);
        }

        var observation = this.telemetry.observe(Operation.COMPUTE_IF_ABSENT);
        var current = KoraContext.current();
        var context = (current != null ? current : KoraContext.empty())
            .with(OpentelemetryContextStorage.OPENTELEMETRY_KEY, Context.current().with(observation.span()));

        return Observation.attach(context, observation).call(() -> {
            observation.observeKey(key);
            try {
                try {
                    final byte[] keyAsBytes = mapKey(key);
                    final byte[] jsonAsBytes = (expireAfterAccessMillis == null)
                        ? redisClient.get(keyAsBytes)
                        : redisClient.getex(keyAsBytes, expireAfterAccessMillis);

                    var fromCache = valueMapper.read(jsonAsBytes);
                    if (fromCache != null) {
                        observation.observeValue(fromCache);
                        return fromCache;
                    }
                } catch (Exception e) {
                    observation.observeError(e);
                }

                final V value;
                try {
                    value = mappingFunction.apply(key);
                } catch (Exception e) {
                    observation.observeError(e);
                    throw e;
                }

                if (value == null) {
                    return null;
                }

                try {
                    try {
                        final byte[] keyAsBytes = mapKey(key);
                        final byte[] valueAsBytes = valueMapper.write(value);
                        if (expireAfterWriteMillis == null) {
                            redisClient.set(keyAsBytes, valueAsBytes);
                        } else {
                            redisClient.psetex(keyAsBytes, valueAsBytes, expireAfterWriteMillis);
                        }
                    } catch (Exception e) {
                        observation.observeError(e);
                    }
                    return value;
                } catch (CompletionException e) {
                    observation.observeError(e.getCause());
                    return value;
                } catch (Exception e) {
                    observation.observeError(e);
                    return value;
                }
            } finally {
                observation.end();
            }
        });
    }

    @Override
    public Map<K, V> computeIfAbsent(Collection<K> keys, Function<Set<K>, Map<K, V>> mappingFunction) {
        if (keys == null || keys.isEmpty()) {
            return mappingFunction.apply(Collections.emptySet());
        }
        if (!enabled) {
            return mappingFunction.apply(Set.copyOf(keys));
        }

        var observation = this.telemetry.observe(Operation.COMPUTE_IF_ABSENT_MANY);
        var current = KoraContext.current();
        var context = (current != null ? current : KoraContext.empty())
            .with(OpentelemetryContextStorage.OPENTELEMETRY_KEY, Context.current().with(observation.span()));

        return Observation.attach(context, observation).call(() -> {
            final Map<K, V> fromCache = new HashMap<>();
            try {
                observation.observeKeys(keys);
                try {
                    final Map<K, byte[]> keysByKeyBytes = keys.stream()
                        .collect(Collectors.toMap(k -> k, this::mapKey, (v1, v2) -> v2));

                    final byte[][] keysByBytes = keysByKeyBytes.values().toArray(byte[][]::new);
                    final Map<byte[], byte[]> valueByKeys = (expireAfterAccessMillis == null)
                        ? redisClient.mget(keysByBytes)
                        : redisClient.getex(keysByBytes, expireAfterAccessMillis);

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
                observation.observeValues(fromCache);

                if (fromCache.size() == keys.size()) {
                    return fromCache;
                }

                var missingKeys = keys.stream()
                    .filter(k -> !fromCache.containsKey(k))
                    .collect(Collectors.toSet());

                final Map<K, V> values;
                try {
                    var mapped = mappingFunction.apply(missingKeys);
                    values = (mapped == null) ? Collections.emptyMap() : mapped;
                } catch (Exception e) {
                    observation.observeError(e);
                    throw e;
                }

                try {
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
                            observation.observeError(e);
                        }
                    }
                    fromCache.putAll(values);
                    return fromCache;
                } catch (CompletionException e) {
                    observation.observeError(e.getCause());
                    return fromCache;
                } catch (Exception e) {
                    observation.observeError(e);
                    return fromCache;
                }
            } finally {
                observation.end();
            }
        });
    }

    @Override
    public void invalidate(K key) {
        if (key == null || !enabled) {
            return;
        }

        var observation = this.telemetry.observe(Operation.INVALIDATE);
        var current = KoraContext.current();
        var context = (current != null ? current : KoraContext.empty())
            .with(OpentelemetryContextStorage.OPENTELEMETRY_KEY, Context.current().with(observation.span()));

        Observation.attach(context, observation).run(() -> {
            observation.observeKey(key);
            try {
                final byte[] keyAsBytes = mapKey(key);
                redisClient.del(keyAsBytes);
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
    public void invalidate(Collection<K> keys) {
        if (keys == null || keys.isEmpty() || !enabled) {
            return;
        }

        var observation = this.telemetry.observe(Operation.INVALIDATE_MANY);
        var current = KoraContext.current();
        var context = (current != null ? current : KoraContext.empty())
            .with(OpentelemetryContextStorage.OPENTELEMETRY_KEY, Context.current().with(observation.span()));

        Observation.attach(context, observation).run(() -> {
            try {
                observation.observeKeys(keys);
                final byte[][] keysAsBytes = keys.stream()
                    .map(this::mapKey)
                    .toArray(byte[][]::new);

                redisClient.del(keysAsBytes);
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
        if (!enabled) {
            return;
        }

        var observation = this.telemetry.observe(Operation.INVALIDATE_ALL);
        var current = KoraContext.current();
        var context = (current != null ? current : KoraContext.empty())
            .with(OpentelemetryContextStorage.OPENTELEMETRY_KEY, Context.current().with(observation.span()));

        Observation.attach(context, observation).run(() -> {
            try {
                observation.observeKeys(List.of());
                if (keyPrefix == null) {
                    logger.warn("Redis Cache key prefix is empty! Initiating flushAll for invalidateAll command.");
                    redisClient.flushAll();
                } else {
                    final List<byte[]> keys = redisClient.scan(keyPrefix);
                    if (keys.isEmpty()) {
                        return;
                    }
                    observation.observeKeys(keys);
                    redisClient.del(keys);
                }
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
