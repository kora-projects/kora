package ru.tinkoff.kora.micrometer.module.resilient;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.BaseUnits;
import ru.tinkoff.kora.resilient.fallback.telemetry.FallbackMetrics;

import javax.annotation.Nonnull;
import java.util.concurrent.ConcurrentHashMap;

public final class MicrometerFallbackMetrics implements FallbackMetrics {

    private record Metrics(Counter attempts) {}

    private final ConcurrentHashMap<String, Metrics> metrics = new ConcurrentHashMap<>();
    private final MeterRegistry registry;

    public MicrometerFallbackMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void recordExecute(@Nonnull String name, @Nonnull Throwable throwable) {
        var metrics = this.metrics.computeIfAbsent(name, k -> build(name));
        metrics.attempts.increment();
    }

    private Metrics build(String name) {
        var attempts = io.micrometer.core.instrument.Counter.builder("resilient.fallback.attempts")
            .baseUnit(BaseUnits.OPERATIONS)
            .tag("type", "executed")
            .tag("name", name)
            .register(registry);

        return new Metrics(attempts);
    }
}
