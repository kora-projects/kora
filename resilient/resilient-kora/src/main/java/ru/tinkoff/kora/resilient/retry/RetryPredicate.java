package ru.tinkoff.kora.resilient.retry;

import javax.annotation.Nonnull;
import java.util.function.Predicate;

/**
 * Configures behavior of {@link Retry} on whenever exception should count as failre or not
 */
public interface RetryPredicate extends Predicate<Throwable> {

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
