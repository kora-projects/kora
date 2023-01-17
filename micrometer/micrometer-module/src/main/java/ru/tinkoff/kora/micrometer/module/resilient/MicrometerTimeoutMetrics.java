package ru.tinkoff.kora.micrometer.module.resilient;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.BaseUnits;
import ru.tinkoff.kora.resilient.timeout.telemetry.TimeoutMetrics;

import javax.annotation.Nonnull;
import java.util.concurrent.ConcurrentHashMap;

public final class MicrometerTimeoutMetrics implements TimeoutMetrics {

    private record Metrics(Counter exhausted) {}

    private final ConcurrentHashMap<String, Metrics> metrics = new ConcurrentHashMap<>();
    private final MeterRegistry registry;

    public MicrometerTimeoutMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void recordTimeout(@Nonnull String name, long timeoutInNanos) {
        var metrics = this.metrics.computeIfAbsent(name, k -> build(name));
        metrics.exhausted.increment();
    }

    private Metrics build(String name) {
        var exhausted = Counter.builder("resilient.timeout.exhausted")
            .baseUnit(BaseUnits.OPERATIONS)
            .tag("name", name)
            .register(registry);

        return new Metrics(exhausted);
    }
}
