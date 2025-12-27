package ru.tinkoff.kora.cache.caffeine;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import io.micrometer.core.instrument.binder.cache.CaffeineStatsCounter;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;

public class DefaultCaffeineCacheFactory implements CaffeineCacheFactory {
    @Nullable
    private final MeterRegistry meterRegistry;

    public DefaultCaffeineCacheFactory(@Nullable MeterRegistry meterRegistry) {
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
        var counter = new CaffeineStatsCounter(this.meterRegistry, name, tags);
        var cache = builder.recordStats(() -> counter)
            .<K, V>build();
        counter.registerSizeMetric(cache);
        CaffeineCacheMetrics.monitor(this.meterRegistry, cache, name, tags);
        return cache;
    }
}
