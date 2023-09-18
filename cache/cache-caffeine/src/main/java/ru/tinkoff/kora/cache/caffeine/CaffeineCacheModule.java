package ru.tinkoff.kora.cache.caffeine;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.cache.telemetry.CacheMetrics;
import ru.tinkoff.kora.cache.telemetry.CacheTracer;
import ru.tinkoff.kora.common.DefaultComponent;

public interface CaffeineCacheModule {

    @DefaultComponent
    default CaffeineCacheTelemetry caffeineCacheTelemetry(@Nullable CacheMetrics metrics, @Nullable CacheTracer tracer) {
        return new CaffeineCacheTelemetry(metrics, tracer);
    }

    @DefaultComponent
    default CaffeineCacheFactory caffeineCacheFactory(@Nullable CaffeineCacheMetricCollector cacheMetricsCollector) {
        return new CaffeineCacheFactory() {
            @Nonnull
            @Override
            public <K, V> Cache<K, V> build(@Nonnull String name, @Nonnull CaffeineCacheConfig config) {
                var builder = Caffeine.newBuilder();
                if (config.expireAfterWrite() != null)
                    builder.expireAfterWrite(config.expireAfterWrite());
                if (config.expireAfterAccess() != null)
                    builder.expireAfterAccess(config.expireAfterAccess());
                if (config.initialSize() != null)
                    builder.initialCapacity(config.initialSize());
                if (config.maximumSize() != null)
                    builder.maximumSize(config.maximumSize());

                final Cache<K, V> cache;
                if (cacheMetricsCollector != null) {
                    cache = builder.recordStats().build();
                    cacheMetricsCollector.register(name, cache);
                } else {
                    cache = builder.build();
                }
                return cache;
            }
        };
    }
}
