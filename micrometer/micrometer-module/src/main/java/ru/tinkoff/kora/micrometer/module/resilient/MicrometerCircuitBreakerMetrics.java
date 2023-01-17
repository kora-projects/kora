package ru.tinkoff.kora.micrometer.module.resilient;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import ru.tinkoff.kora.resilient.circuitbreaker.CircuitBreaker.State;
import ru.tinkoff.kora.resilient.circuitbreaker.telemetry.CircuitBreakerMetrics;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class MicrometerCircuitBreakerMetrics implements CircuitBreakerMetrics {

    private final Map<String, AtomicInteger> metrics = new ConcurrentHashMap<>();
    private final MeterRegistry registry;

    public MicrometerCircuitBreakerMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void recordState(@Nonnull String name, @Nonnull State newState) {
        final AtomicInteger state = metrics.computeIfAbsent(name, k -> {
            final AtomicInteger gaugeState = new AtomicInteger(asIntState(newState));
            Gauge.builder("resilient.circuitbreaker.state", gaugeState::get)
                .tag("name", name)
                .description("Circuit Breaker state metrics, where 0 -> CLOSED, 1 -> HALF_OPEN, 2 -> OPEN")
                .register(registry);
            return gaugeState;
        });

        state.set(asIntState(newState));
    }

    private int asIntState(State state) {
        return switch (state) {
            case CLOSED -> 0;
            case HALF_OPEN -> 1;
            case OPEN -> 2;
        };
    }
}
