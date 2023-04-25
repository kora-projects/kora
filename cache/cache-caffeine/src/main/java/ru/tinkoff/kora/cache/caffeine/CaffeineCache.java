package ru.tinkoff.kora.cache.caffeine;

import com.github.benmanes.caffeine.cache.Cache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.cache.telemetry.CacheTelemetry;

import javax.annotation.Nonnull;

final class CaffeineCache<K, V> implements ru.tinkoff.kora.cache.Cache<K, V> {

    private static final Logger logger = LoggerFactory.getLogger(CaffeineCache.class);

    private final String name;
    private final Cache<K, V> caffeine;
    private final CacheTelemetry telemetry;

    CaffeineCache(String name, Cache<K, V> caffeine, CacheTelemetry telemetry) {
        this.name = name;
        this.caffeine = caffeine;
        this.telemetry = telemetry;
    }

    @Nonnull
    String origin() {
        return "caffeine";
    }

    @Override
    public V get(@Nonnull K key) {
        logger.trace("Looking for value in cache '{}' for key: {}", name, key);
        final CacheTelemetry.TelemetryContext telemetryContext = telemetry.create(CacheTelemetry.Operation.Type.GET, name, origin());
        telemetryContext.startRecording();

        final V v = caffeine.getIfPresent(key);
        if (v == null) {
            logger.trace("Value NOT found in cache '{}' for key: {}", name, key);
        } else {
            logger.debug("Value found in cache '{}' for key: {}", name, key);
        }

        telemetryContext.recordSuccess(v);
        return v;
    }

    @Nonnull
    public V put(@Nonnull K key, @Nonnull V value) {
        logger.trace("Putting value in cache '{}' for key: {}", name, key);
        final CacheTelemetry.TelemetryContext telemetryContext = telemetry.create(CacheTelemetry.Operation.Type.PUT, name, origin());
        telemetryContext.startRecording();

        caffeine.put(key, value);

        telemetryContext.recordSuccess();
        return value;
    }

    @Override
    public void invalidate(@Nonnull K key) {
        logger.trace("Invalidating value in cache '{}' for key: {}", name, key);
        final CacheTelemetry.TelemetryContext telemetryContext = telemetry.create(CacheTelemetry.Operation.Type.INVALIDATE, name, origin());
        telemetryContext.startRecording();

        caffeine.invalidate(key);

        telemetryContext.recordSuccess();
    }

    @Override
    public void invalidateAll() {
        logger.trace("Invalidating all values in cache '{}'", name);
        final CacheTelemetry.TelemetryContext telemetryContext = telemetry.create(CacheTelemetry.Operation.Type.INVALIDATE_ALL, name, origin());
        telemetryContext.startRecording();

        caffeine.invalidateAll();

        telemetryContext.recordSuccess();
    }

    @Nonnull
    @Override
    public Mono<V> getAsync(@Nonnull K key) {
        return Mono.fromCallable(() -> get(key));
    }

    @Nonnull
    @Override
    public Mono<V> putAsync(@Nonnull K key, @Nonnull V value) {
        return Mono.fromCallable(() -> put(key, value));
    }

    @Nonnull
    @Override
    public Mono<Void> invalidateAsync(@Nonnull K key) {
        return Mono.fromRunnable(() -> invalidate(key));
    }

    @Nonnull
    @Override
    public Mono<Void> invalidateAllAsync() {
        return Mono.fromRunnable(this::invalidateAll);
    }
}
