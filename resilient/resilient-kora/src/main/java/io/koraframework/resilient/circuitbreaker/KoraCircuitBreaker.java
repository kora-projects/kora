package io.koraframework.resilient.circuitbreaker;

import io.koraframework.resilient.circuitbreaker.exception.CallNotPermittedException;
import io.koraframework.resilient.circuitbreaker.telemetry.CircuitBreakerTelemetry;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

public class KoraCircuitBreaker implements CircuitBreaker {

    private final CircuitBreaker delegate;

    public KoraCircuitBreaker(String name,
                              CircuitBreakerConfig config,
                              @Nullable CircuitBreakerPredicate failurePredicate,
                              CircuitBreakerTelemetry telemetry) {
        CircuitBreakerConfig.validate(name, config);
        CircuitBreakerPredicate predicate = failurePredicate == null ? this::test : failurePredicate;
        this.delegate = switch (config.type()) {
            case FIXED_WINDOW -> new FixedWindowKoraCircuitBreaker(name, config, predicate, telemetry);
            case STRIPED_APPROX -> new StripedApproxKoraCircuitBreaker(name, config, predicate, telemetry);
            case RING_BUFFER -> new RingBufferKoraCircuitBreaker(name, config, predicate, telemetry);
            case TIME_BASED -> new TimeBasedKoraCircuitBreaker(name, config, predicate, telemetry);
        };
    }

    @Override
    public <T> T accept(Supplier<T> callable) throws CallNotPermittedException {
        return this.delegate.accept(callable);
    }

    @Override
    public <T> T accept(Supplier<T> callable, Supplier<T> fallback) throws CallNotPermittedException {
        return this.delegate.accept(callable, fallback);
    }

    @Override
    public boolean tryAcquire() {
        return this.delegate.tryAcquire();
    }

    @Override
    public void acquire() throws CallNotPermittedException {
        this.delegate.acquire();
    }

    @Override
    public void releaseOnSuccess() {
        this.delegate.releaseOnSuccess();
    }

    @Override
    public void releaseOnError(Throwable throwable) {
        this.delegate.releaseOnError(throwable);
    }

    State getState() {
        if (this.delegate instanceof FixedWindowKoraCircuitBreaker circuitBreaker) {
            return circuitBreaker.getState();
        } else if (this.delegate instanceof StripedApproxKoraCircuitBreaker circuitBreaker) {
            return circuitBreaker.getState();
        } else if (this.delegate instanceof RingBufferKoraCircuitBreaker circuitBreaker) {
            return circuitBreaker.getState();
        } else if (this.delegate instanceof TimeBasedKoraCircuitBreaker circuitBreaker) {
            return circuitBreaker.getState();
        } else {
            throw new IllegalStateException("Unknown CircuitBreaker implementation: " + this.delegate.getClass());
        }
    }

    CircuitBreaker delegate() {
        return this.delegate;
    }
}
