package io.koraframework.resilient.retry;

import org.jspecify.annotations.Nullable;

/**
 * Factory that produces the {@link RetryBudget} used by a {@link Retry} instance.
 *
 * <p>A default no-tag implementation is provided by {@link RetryModule}. Users may override the budget for all retries by
 * registering their own {@link RetryBudgetFactory} component, or for a single retry by registering one tagged with that
 * retry's contract type. When a tagged factory is available it takes precedence over the default one.
 */
public interface RetryBudgetFactory {

    /**
     * Builds the retry budget for the given retry.
     *
     * @param name   the retry name
     * @param config the retry configuration
     * @return the retry budget, or {@code null} if the budget is disabled for this retry
     */
    @Nullable
    RetryBudget get(String name, RetryConfig config);
}
