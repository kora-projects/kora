package ru.tinkoff.kora.cache.caffeine;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.cache.telemetry.CacheTelemetry;
import ru.tinkoff.kora.cache.telemetry.CacheTelemetryArgs;
import ru.tinkoff.kora.cache.telemetry.CacheTelemetryFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public abstract class AbstractCaffeineCache<K, V> implements CaffeineCache<K, V> {

    private static final String ORIGIN = "caffeine";

    private final com.github.benmanes.caffeine.cache.Cache<K, V> caffeine;
    private final CacheTelemetry telemetry;

    @Deprecated
    protected AbstractCaffeineCache(String name,
                                    CaffeineCacheConfig config,
                                    CaffeineCacheFactory factory,
                                    CaffeineCacheTelemetry telemetry) {
        this.caffeine = factory.build(name, config);
        this.telemetry = operationName -> {
            var telemetryContext = telemetry.create(operationName, name);
            return new CacheTelemetry.CacheTelemetryContext() {
                @Override
                public void recordSuccess(@Nullable Object valueFromCache) {
                    if (valueFromCache == null) {
                        telemetryContext.recordSuccess();
                    } else {
                        telemetryContext.recordSuccess(valueFromCache);
                    }
                }

                @Override
                public void recordFailure(@Nullable Throwable throwable) {
                    telemetryContext.recordFailure(throwable);
                }
            };
        };
    }

    protected AbstractCaffeineCache(String name,
                                    CaffeineCacheConfig config,
                                    CaffeineCacheFactory factory,
                                    CacheTelemetryFactory telemetry) {
        this.caffeine = factory.build(name, config);
        this.telemetry = telemetry.get(config.telemetry(), new CacheTelemetryArgs() {
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
    }

    @Override
    public V get(@Nonnull K key) {
        if (key == null) {
            return null;
        }

        var telemetryContext = telemetry.get("GET");
        var value = caffeine.getIfPresent(key);
        telemetryContext.recordSuccess(value);
        return value;
    }

    @Nonnull
    @Override
    public Map<K, V> get(@Nonnull Collection<K> keys) {
        if (keys == null || keys.isEmpty()) {
            return Collections.emptyMap();
        }

        var telemetryContext = telemetry.get("GET_MANY");
        var values = caffeine.getAllPresent(keys);
        telemetryContext.recordSuccess(values);
        return values;
    }

    @Nonnull
    @Override
    public Map<K, V> getAll() {
        var telemetryContext = telemetry.get("GET_ALL");
        var values = Collections.unmodifiableMap(caffeine.asMap());
        telemetryContext.recordSuccess(null);
        return values;
    }

    @Override
    public V computeIfAbsent(@Nonnull K key, @Nonnull Function<K, V> mappingFunction) {
        if (key == null) {
            return mappingFunction.apply(key);
        }

        var telemetryContext = telemetry.get("COMPUTE_IF_ABSENT");
        var value = caffeine.get(key, mappingFunction);
        telemetryContext.recordSuccess(null);
        return value;
    }

    @SuppressWarnings("unchecked")
    @Nonnull
    @Override
    public Map<K, V> computeIfAbsent(@Nonnull Collection<K> keys, @Nonnull Function<Set<K>, Map<K, V>> mappingFunction) {
        if (keys == null || keys.isEmpty()) {
            return mappingFunction.apply(Collections.emptySet());
        }

        var telemetryContext = telemetry.get("COMPUTE_IF_ABSENT_MANY");
        var value = caffeine.getAll(keys, ks -> mappingFunction.apply((Set<K>) ks));
        telemetryContext.recordSuccess(null);
        return value;
    }

    @Nonnull
    public V put(@Nonnull K key, @Nonnull V value) {
        if (key == null || value == null) {
            return value;
        }

        var telemetryContext = telemetry.get("PUT");
        caffeine.put(key, value);
        telemetryContext.recordSuccess(null);
        return value;
    }

    @Nonnull
    @Override
    public Map<K, V> put(@Nonnull Map<K, V> keyAndValues) {
        if (keyAndValues == null || keyAndValues.isEmpty()) {
            return Collections.emptyMap();
        }

        var telemetryContext = telemetry.get("PUT_MANY");
        caffeine.putAll(keyAndValues);
        telemetryContext.recordSuccess(null);
        return keyAndValues;
    }

    @Override
    public void invalidate(@Nonnull K key) {
        if (key != null) {
            var telemetryContext = telemetry.get("INVALIDATE");
            caffeine.invalidate(key);
            telemetryContext.recordSuccess(null);
        }
    }

    @Override
    public void invalidate(@Nonnull Collection<K> keys) {
        if (keys != null && !keys.isEmpty()) {
            var telemetryContext = telemetry.get("INVALIDATE_MANY");
            caffeine.invalidateAll(keys);
            telemetryContext.recordSuccess(null);
        }
    }

    @Override
    public void invalidateAll() {
        var telemetryContext = telemetry.get("INVALIDATE_ALL");
        caffeine.invalidateAll();
        telemetryContext.recordSuccess(null);
    }
}
