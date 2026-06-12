package io.koraframework.micrometer.module.resilient;

import io.koraframework.resilient.ratelimiter.RateLimiterMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.BaseUnits;

import java.util.concurrent.ConcurrentHashMap;

public final class MicrometerRateLimiterMetrics implements RateLimiterMetrics {

    private record Metrics(Counter acquired, Counter rejected) {}

    private final ConcurrentHashMap<String, Metrics> metrics = new ConcurrentHashMap<>();
    private final MeterRegistry registry;

    public MicrometerRateLimiterMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void recordAcquire(String name, boolean acquired) {
        var metrics = this.metrics.computeIfAbsent(name, k -> build(name));
        if (acquired) {
            metrics.acquired().increment();
        } else {
            metrics.rejected().increment();
        }
    }

    private Metrics build(String name) {
        var acquired = Counter.builder("resilient.ratelimiter.acquire")
            .baseUnit(BaseUnits.OPERATIONS)
            .tag("name", name)
            .tag("status", "acquired")
            .register(registry);

        var rejected = Counter.builder("resilient.ratelimiter.acquire")
            .baseUnit(BaseUnits.OPERATIONS)
            .tag("name", name)
            .tag("status", "rejected")
            .register(registry);

        return new Metrics(acquired, rejected);
    }
}
