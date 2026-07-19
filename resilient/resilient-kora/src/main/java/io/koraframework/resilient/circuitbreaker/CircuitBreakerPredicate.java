package io.koraframework.resilient.circuitbreaker;


import java.util.function.Predicate;

/**
 * Configures behavior of {@link CircuitBreaker#releaseOnError(Throwable)} on whenever exception should count as failre or not
 */
@FunctionalInterface
public interface CircuitBreakerPredicate extends Predicate<Throwable> {

    /**
     * @param throwable to test
     * @return when True than throwable is registered as failure
     */
    @Override
    boolean test(Throwable throwable);
}
