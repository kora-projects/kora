package ru.tinkoff.kora.micrometer.module.resilient;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.BaseUnits;
import jakarta.annotation.Nonnull;
import ru.tinkoff.kora.resilient.circuitbreaker.CircuitBreaker;
import ru.tinkoff.kora.resilient.circuitbreaker.CircuitBreakerMetrics;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class MicrometerCircuitBreakerMetrics implements CircuitBreakerMetrics {

    private final Map<String, StateMetrics> metrics = new ConcurrentHashMap<>();
    private final MeterRegistry registry;

    private record StateMetrics(AtomicInteger stateValue,
                                Gauge state,
                                Counter transitionOpen,
                                Counter transitionHalfOpen,
                                Counter acquireHalfOpenPermitted,
                                Counter acquireHalfOpenRejected,
                                Counter acquireOpenRejected) {}

    public MicrometerCircuitBreakerMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void recordCallAcquire(@Nonnull String name, @Nonnull CallAcquireStatus callStatus) {
        final StateMetrics stateMetrics = getCircuitBreakerMetrics(name, CircuitBreaker.State.CLOSED);
        switch (stateMetrics.stateValue.get()) {
            case 1: // HALF_OPEN
                if (callStatus == CallAcquireStatus.PERMITTED) {
                    stateMetrics.acquireHalfOpenPermitted.increment();
                } else {
                    stateMetrics.acquireHalfOpenRejected.increment();
                }
            case 2: // OPEN
                if (callStatus == CallAcquireStatus.REJECTED) {
                    stateMetrics.acquireOpenRejected.increment();
                }
            case 0: // CLOSED ignored cause useless
            default: // do nothing
        }
    }

    @Override
    public void recordState(@Nonnull String name, @Nonnull CircuitBreaker.State newState) {
        final StateMetrics stateMetrics = getCircuitBreakerMetrics(name, newState);

        stateMetrics.stateValue().set(asIntState(newState));

        if (newState == CircuitBreaker.State.OPEN) {
            stateMetrics.transitionOpen().increment();
        } else if (newState == CircuitBreaker.State.HALF_OPEN) {
            stateMetrics.transitionHalfOpen().increment();
        }
    }

    private StateMetrics getCircuitBreakerMetrics(@Nonnull String name, @Nonnull CircuitBreaker.State initiaState) {
        return metrics.computeIfAbsent(name, k -> {
            final AtomicInteger gaugeState = new AtomicInteger(asIntState(initiaState));
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

            final Counter acquireHalfOpenPermitted = Counter.builder("resilient.circuitbreaker.call.acquire")
                .baseUnit(BaseUnits.OPERATIONS)
                .tag("name", name)
                .tag("state", CircuitBreaker.State.HALF_OPEN.name())
                .tag("status", CallAcquireStatus.PERMITTED.name())
                .register(registry);

            final Counter acquireHalfOpenRejected = Counter.builder("resilient.circuitbreaker.call.acquire")
                .baseUnit(BaseUnits.OPERATIONS)
                .tag("name", name)
                .tag("state", CircuitBreaker.State.HALF_OPEN.name())
                .tag("status", CallAcquireStatus.REJECTED.name())
                .register(registry);

            final Counter acquireOpenRejected = Counter.builder("resilient.circuitbreaker.call.acquire")
                .baseUnit(BaseUnits.OPERATIONS)
                .tag("name", name)
                .tag("state", CircuitBreaker.State.OPEN.name())
                .tag("status", CallAcquireStatus.REJECTED.name())
                .register(registry);

            return new StateMetrics(gaugeState, state, transOpen, transHalfOpen, acquireHalfOpenPermitted, acquireHalfOpenRejected, acquireOpenRejected);
        });
    }

    private int asIntState(CircuitBreaker.State state) {
        return switch (state) {
            case CLOSED -> 0;
            case HALF_OPEN -> 1;
            case OPEN -> 2;
        };
    }
}
