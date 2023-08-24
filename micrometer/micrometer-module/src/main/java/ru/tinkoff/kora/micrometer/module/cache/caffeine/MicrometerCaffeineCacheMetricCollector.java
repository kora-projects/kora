package ru.tinkoff.kora.micrometer.module.cache.caffeine;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import ru.tinkoff.kora.cache.caffeine.CaffeineCacheMetricCollector;

public final class MicrometerCaffeineCacheMetricCollector implements CaffeineCacheMetricCollector {

    private final MeterRegistry meterRegistry;

    public MicrometerCaffeineCacheMetricCollector(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void register(String cacheName, com.github.benmanes.caffeine.cache.Cache<?, ?> cache) {
        CaffeineCacheMetrics.monitor(meterRegistry, cache, cacheName);
    }
}
