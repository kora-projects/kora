package ru.tinkoff.kora.resilient.fallback;

import javax.annotation.Nonnull;
import java.util.function.Predicate;

/**
 * Configures behavior of Fallback on whenever exception should count as fallback applicable or not
 */
public interface FallbackFailurePredicate extends Predicate<Throwable> {

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
