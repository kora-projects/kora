package io.koraframework.cache.caffeine;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;

public class CaffeineFactory implements CaffeineCacheFactory {

    @Nullable
    private final MeterRegistry meterRegistry;

    public CaffeineFactory(@Nullable MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public <K, V> Cache<K, V> build(String name, CaffeineCacheConfig config) {
        var builder = Caffeine.newBuilder();
        if (config.expireAfterWrite() != null) {
            builder.expireAfterWrite(config.expireAfterWrite());
        }
        if (config.expireAfterAccess() != null) {
            builder.expireAfterAccess(config.expireAfterAccess());
        }
        if (config.initialSize() != null) {
            builder.initialCapacity(config.initialSize());
        }

        builder.maximumSize(config.maximumSize());

        if (!config.telemetry().metrics().enabled() || this.meterRegistry == null) {
            return builder.build();
        }

        var tags = new ArrayList<Tag>();
        for (var e : config.telemetry().metrics().tags().entrySet()) {
            tags.add(Tag.of(e.getKey(), e.getValue()));
        }
        // Statistics are recorded by Caffeine itself and published by the Micrometer binder. Feeding
        // CaffeineStatsCounter to recordStats() as well would register cache.gets and friends twice,
        // as plain counters and then as function counters, and the second registration fails.
        var cache = builder.recordStats().<K, V>build();
        CaffeineCacheMetrics.monitor(this.meterRegistry, cache, name, tags);
        return cache;
    }
}
