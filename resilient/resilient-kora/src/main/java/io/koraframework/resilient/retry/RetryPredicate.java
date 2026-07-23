package io.koraframework.resilient.retry;

import java.util.function.Predicate;

/**
 * Configures behavior of {@link Retry} on whenever exception should count as failre or not
 */
@FunctionalInterface
public interface RetryPredicate extends Predicate<Throwable> {

    /**
     * @param throwable to test
     * @return when True than throwable is registered as failure
     */
    @Override
    boolean test(Throwable throwable);
}
