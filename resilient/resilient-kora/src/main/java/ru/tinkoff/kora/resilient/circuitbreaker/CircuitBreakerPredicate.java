package ru.tinkoff.kora.resilient.circuitbreaker;


import java.util.function.Predicate;

/**
 * Configures behavior of {@link CircuitBreaker#releaseOnError(Throwable)} on whenever exception should count as failre or not
 */
public interface CircuitBreakerPredicate extends Predicate<Throwable> {

    /**
     * @return name of the predicate
     */
    String name();

    /**
     * @param throwable to test
     * @return when True than throwable is registered as failure
     */
    @Override
    boolean test(Throwable throwable);
}
