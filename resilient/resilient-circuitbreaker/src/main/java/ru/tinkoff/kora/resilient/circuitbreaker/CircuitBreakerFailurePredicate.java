package ru.tinkoff.kora.resilient.circuitbreaker;


import javax.annotation.Nonnull;
import java.util.function.Predicate;

/**
 * Configures behavior of {@link CircuitBreaker#releaseOnError(Throwable)} on whenever exception should count as failre or not
 */
public interface CircuitBreakerFailurePredicate extends Predicate<Throwable> {

    /**
     * @return name of the predicate
     */
    @Nonnull
    String name();

    /**
     * @param throwable to test
     * @return when True than throwable is registered as failure
     */
    @Override
    boolean test(@Nonnull Throwable throwable);
}
