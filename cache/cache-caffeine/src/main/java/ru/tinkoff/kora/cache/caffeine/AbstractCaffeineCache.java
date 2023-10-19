package ru.tinkoff.kora.cache.caffeine;

import jakarta.annotation.Nonnull;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

public abstract class AbstractCaffeineCache<K, V> implements CaffeineCache<K, V> {

    private final String name;
    private final com.github.benmanes.caffeine.cache.Cache<K, V> caffeine;
    private final CaffeineCacheTelemetry telemetry;

    protected AbstractCaffeineCache(String name,
                                    CaffeineCacheConfig config,
                                    CaffeineCacheFactory factory,
                                    CaffeineCacheTelemetry telemetry) {
        this.name = name;
        this.caffeine = factory.build(name, config);
        this.telemetry = telemetry;
    }

    @Override
    public V get(@Nonnull K key) {
        if (key == null) {
            return null;
        }

        var telemetryContext = telemetry.create("GET", name);
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

        var telemetryContext = telemetry.create("GET_MANY", name);
        var values = caffeine.getAllPresent(keys);
        telemetryContext.recordSuccess();
        return values;
    }

    @Nonnull
    @Override
    public Map<K, V> getAll() {
        var telemetryContext = telemetry.create("GET_ALL", name);
        var values = Collections.unmodifiableMap(caffeine.asMap());
        telemetryContext.recordSuccess();
        return values;
    }

    @Override
    public V computeIfAbsent(@Nonnull K key, @Nonnull Function<K, V> mappingFunction) {
        if (key == null) {
            return mappingFunction.apply(key);
        }

        var telemetryContext = telemetry.create("COMPUTE_IF_ABSENT", name);
        var value = caffeine.get(key, mappingFunction);
        telemetryContext.recordSuccess();
        return value;
    }

    @SuppressWarnings("unchecked")
    @Nonnull
    @Override
    public Map<K, V> computeIfAbsent(@Nonnull Collection<K> keys, @Nonnull Function<Set<K>, Map<K, V>> mappingFunction) {
        if (keys == null || keys.isEmpty()) {
            return mappingFunction.apply(Collections.emptySet());
        }

        var telemetryContext = telemetry.create("COMPUTE_IF_ABSENT_MANY", name);
        var value = caffeine.getAll(keys, ks -> mappingFunction.apply((Set<K>) ks));
        telemetryContext.recordSuccess();
        return value;
    }

    @Nonnull
    public V put(@Nonnull K key, @Nonnull V value) {
        if (key == null || value == null) {
            return value;
        }

        var telemetryContext = telemetry.create("PUT", name);
        caffeine.put(key, value);
        telemetryContext.recordSuccess();
        return value;
    }

    @Nonnull
    @Override
    public Map<K, V> put(@Nonnull Map<K, V> keyAndValues) {
        if (keyAndValues == null || keyAndValues.isEmpty()) {
            return Collections.emptyMap();
        }

        var telemetryContext = telemetry.create("PUT_MANY", name);
        caffeine.putAll(keyAndValues);
        telemetryContext.recordSuccess();
        return keyAndValues;
    }

    @Override
    public void invalidate(@Nonnull K key) {
        if (key != null) {
            var telemetryContext = telemetry.create("INVALIDATE", name);
            caffeine.invalidate(key);
            telemetryContext.recordSuccess();
        }
    }

    @Override
    public void invalidate(@Nonnull Collection<K> keys) {
        if (keys != null && !keys.isEmpty()) {
            var telemetryContext = telemetry.create("INVALIDATE_MANY", name);
            caffeine.invalidateAll(keys);
            telemetryContext.recordSuccess();
        }
    }

    @Override
    public void invalidateAll() {
        var telemetryContext = telemetry.create("INVALIDATE_ALL", name);
        caffeine.invalidateAll();
        telemetryContext.recordSuccess();
    }
}
