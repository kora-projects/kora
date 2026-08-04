package io.koraframework.resilient.circuitbreaker;


/**
 * Configures behavior of {@link CircuitBreaker#releaseOnError(Throwable)} on whenever exception should count as failre or not
 */
@FunctionalInterface
public interface CircuitBreakerPredicate {

    /**
     * @param throwable to test
     * @return when True than throwable is registered as failure
     */
    boolean isCircuitBreakerFailure(Throwable throwable);
}
