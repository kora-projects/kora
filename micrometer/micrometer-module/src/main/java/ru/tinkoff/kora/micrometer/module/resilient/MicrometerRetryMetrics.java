package ru.tinkoff.kora.micrometer.module.resilient;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.BaseUnits;
import ru.tinkoff.kora.resilient.retry.RetryMetrics;

import javax.annotation.Nonnull;
import java.util.concurrent.ConcurrentHashMap;

public final class MicrometerRetryMetrics implements RetryMetrics {

    private record Metrics(Counter exhausted, Counter attempts) {}

    private final ConcurrentHashMap<String, Metrics> metrics = new ConcurrentHashMap<>();
    private final MeterRegistry registry;

    public MicrometerRetryMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void recordAttempt(@Nonnull String name, long delayInNanos) {
        final Metrics metrics = this.metrics.computeIfAbsent(name, k -> build(name));
        metrics.attempts().increment();
    }

    @Override
    public void recordExhaustedAttempts(@Nonnull String name, int totalAttempts) {
        var metrics = this.metrics.computeIfAbsent(name, k -> build(name));
        metrics.exhausted().increment();
    }

    private Metrics build(String name) {
        var attempts = Counter.builder("resilient.retry.attempts")
            .baseUnit(BaseUnits.OPERATIONS)
            .tag("name", name)
            .register(registry);

        var exhausted = Counter.builder("resilient.retry.exhausted")
            .baseUnit(BaseUnits.OPERATIONS)
            .tag("name", name)
            .register(registry);

        return new Metrics(attempts, exhausted);
    }
}
