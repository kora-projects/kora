package io.koraframework.resilient.retry;

/**
 * Retry budget that limits the rate of retries to protect a downstream dependency from retry storms.
 *
 * <p>The default implementation is {@link KoraRetryBudget}. A custom implementation can be supplied through a
 * {@link RetryBudgetFactory} component.
 */
public interface RetryBudget {

    /**
     * Tries to acquire a token permitting a single retry attempt.
     *
     * @return {@code true} if a retry token was acquired, {@code false} if the budget is exhausted
     */
    boolean tryAcquireRetryToken();

    /**
     * Records a successful call, replenishing the budget.
     */
    void onSuccess();

    /**
     * @return the number of retry tokens currently available
     */
    double availableTokens();
}
