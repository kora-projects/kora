package ru.tinkoff.kora.cache.caffeine;

import org.jetbrains.annotations.ApiStatus.Internal;
import reactor.core.publisher.Mono;

import javax.annotation.Nonnull;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

@Internal
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

    @Override
    public V computeIfAbsent(@Nonnull K key, @Nonnull Function<K, V> mappingFunction) {
        if (key == null) {
            return null;
        }

        var telemetryContext = telemetry.create("PUT_IF_ABSENT", name);
        var value = caffeine.get(key, mappingFunction);
        telemetryContext.recordSuccess();
        return value;
    }

    @SuppressWarnings("unchecked")
    @Nonnull
    @Override
    public Map<K, V> computeIfAbsent(@Nonnull Collection<K> keys, @Nonnull Function<Set<K>, Map<K, V>> mappingFunction) {
        if (keys == null || keys.isEmpty()) {
            return Collections.emptyMap();
        }

        var telemetryContext = telemetry.create("PUT_IF_ABSENT_MANY", name);
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

    @Nonnull
    @Override
    public Mono<V> getAsync(@Nonnull K key) {
        return (key == null)
            ? Mono.empty()
            : Mono.fromCallable(() -> get(key));
    }

    @Nonnull
    @Override
    public Mono<Map<K, V>> getAsync(@Nonnull Collection<K> keys) {
        return (keys == null || keys.isEmpty())
            ? Mono.just(Collections.emptyMap())
            : Mono.fromCallable(() -> get(keys));
    }

    @Nonnull
    @Override
    public Mono<V> putAsync(@Nonnull K key, @Nonnull V value) {
        if (key == null) {
            return Mono.justOrEmpty(value);
        }

        return Mono.fromCallable(() -> put(key, value));
    }

    @Override
    public Mono<V> computeIfAbsentAsync(@Nonnull K key, @Nonnull Function<K, Mono<V>> mappingFunction) {
        if (key == null) {
            return Mono.empty();
        }

        return Mono.fromCallable(() -> computeIfAbsent(key, (k) -> mappingFunction.apply(k).block(Duration.ofMinutes(5))));
    }

    @Nonnull
    @Override
    public Mono<Map<K, V>> computeIfAbsentAsync(@Nonnull Collection<K> keys, @Nonnull Function<Set<K>, Mono<Map<K, V>>> mappingFunction) {
        if (keys == null || keys.isEmpty()) {
            return Mono.empty();
        }

        return Mono.fromCallable(() -> computeIfAbsent(keys, (k) -> mappingFunction.apply(k).block(Duration.ofMinutes(5))));
    }

    @Nonnull
    @Override
    public Mono<Boolean> invalidateAsync(@Nonnull K key) {
        if (key == null) {
            return Mono.just(false);
        }

        return Mono.fromCallable(() -> {
            invalidate(key);
            return true;
        });
    }

    @Override
    public Mono<Boolean> invalidateAsync(@Nonnull Collection<K> keys) {
        if (keys == null || keys.isEmpty()) {
            return Mono.just(false);
        }

        return Mono.fromCallable(() -> {
            invalidate(keys);
            return true;
        });
    }

    @Nonnull
    @Override
    public Mono<Boolean> invalidateAllAsync() {
        return Mono.fromCallable(() -> {
            invalidateAll();
            return true;
        });
    }
}
