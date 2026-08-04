package io.koraframework.resilient.circuitbreaker;

import io.koraframework.resilient.circuitbreaker.exception.CallNotPermittedException;
import io.koraframework.resilient.circuitbreaker.telemetry.CircuitBreakerTelemetry;
import io.koraframework.resilient.common.ThrowableCallable;
import io.koraframework.resilient.common.ThrowableRunnable;
import org.jspecify.annotations.Nullable;

public class KoraCircuitBreaker implements CircuitBreaker {

    private final CircuitBreaker delegate;

    public KoraCircuitBreaker(String name,
                              CircuitBreakerConfig config,
                              @Nullable CircuitBreakerPredicate failurePredicate,
                              CircuitBreakerTelemetry telemetry) {
        CircuitBreakerConfig.validate(name, config);
        CircuitBreakerPredicate predicate = failurePredicate == null ? this::isFailure : failurePredicate;
        this.delegate = switch (config.type()) {
            case FIXED_WINDOW -> new FixedWindowKoraCircuitBreaker(name, config, predicate, telemetry);
            case STRIPED_APPROX -> new StripedApproxKoraCircuitBreaker(name, config, predicate, telemetry);
            case RING_BUFFER -> new RingBufferKoraCircuitBreaker(name, config, predicate, telemetry);
            case TIME_BASED -> new TimeBasedKoraCircuitBreaker(name, config, predicate, telemetry);
        };
    }

    @Override
    public <E extends Throwable> void accept(ThrowableRunnable<E> runnable) throws E, CallNotPermittedException {
        this.delegate.accept(runnable);
    }

    @Override
    public <T, E extends Throwable> T accept(ThrowableCallable<T, E> callable) throws E, CallNotPermittedException {
        return this.delegate.accept(callable);
    }

    @Override
    public <T, E extends Throwable> T accept(ThrowableCallable<T, E> callable, ThrowableCallable<T, E> fallback) throws E, CallNotPermittedException {
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
        return switch (this.delegate) {
            case FixedWindowKoraCircuitBreaker circuitBreaker -> circuitBreaker.getState();
            case StripedApproxKoraCircuitBreaker circuitBreaker -> circuitBreaker.getState();
            case RingBufferKoraCircuitBreaker circuitBreaker -> circuitBreaker.getState();
            case TimeBasedKoraCircuitBreaker circuitBreaker -> circuitBreaker.getState();
            default -> throw new IllegalStateException("Unknown CircuitBreaker implementation: " + this.delegate.getClass());
        };
    }

    CircuitBreaker delegate() {
        return this.delegate;
    }
}
