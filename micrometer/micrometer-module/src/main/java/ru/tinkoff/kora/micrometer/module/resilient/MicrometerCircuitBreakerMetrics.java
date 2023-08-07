package ru.tinkoff.kora.micrometer.module.resilient;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.BaseUnits;
import ru.tinkoff.kora.resilient.circuitbreaker.CircuitBreaker;
import ru.tinkoff.kora.resilient.circuitbreaker.CircuitBreakerMetrics;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class MicrometerCircuitBreakerMetrics implements CircuitBreakerMetrics {

    private final Map<String, StateMetrics> metrics = new ConcurrentHashMap<>();
    private final MeterRegistry registry;

    private record StateMetrics(AtomicInteger stateValue, Gauge state, Counter transitionOpen, Counter transitionHalfOpen) { }

    public MicrometerCircuitBreakerMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void recordState(@Nonnull String name, @Nonnull CircuitBreaker.State newState) {
        final StateMetrics stateMetrics = metrics.computeIfAbsent(name, k -> {
            final AtomicInteger gaugeState = new AtomicInteger(asIntState(newState));
            final Gauge state = Gauge.builder("resilient.circuitbreaker.state", gaugeState::get)
                .tag("name", name)
                .description("Circuit Breaker state metrics, where 0 -> CLOSED, 1 -> HALF_OPEN, 2 -> OPEN")
                .register(registry);

            final Counter transOpen = Counter.builder("resilient.circuitbreaker.transition")
                .baseUnit(BaseUnits.OPERATIONS)
                .tag("name", name)
                .tag("state", CircuitBreaker.State.OPEN.name())
                .register(registry);

            final Counter transHalfOpen = Counter.builder("resilient.circuitbreaker.transition")
                .baseUnit(BaseUnits.OPERATIONS)
                .tag("name", name)
                .tag("state", CircuitBreaker.State.HALF_OPEN.name())
                .register(registry);

            return new StateMetrics(gaugeState, state, transOpen, transHalfOpen);
        });

        stateMetrics.stateValue().set(asIntState(newState));

        if(newState == CircuitBreaker.State.OPEN) {
            stateMetrics.transitionOpen().increment();
        } else if(newState == CircuitBreaker.State.HALF_OPEN) {
            stateMetrics.transitionHalfOpen().increment();
        }
    }

    private int asIntState(CircuitBreaker.State state) {
        return switch (state) {
            case CLOSED -> 0;
            case HALF_OPEN -> 1;
            case OPEN -> 2;
        };
    }
}
