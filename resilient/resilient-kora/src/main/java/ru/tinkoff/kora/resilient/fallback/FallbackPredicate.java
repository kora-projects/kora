package ru.tinkoff.kora.resilient.fallback;

import java.util.function.Predicate;

/**
 * Configures behavior of Fallback on whenever exception should count as fallback applicable or not
 */
public interface FallbackPredicate extends Predicate<Throwable> {

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
