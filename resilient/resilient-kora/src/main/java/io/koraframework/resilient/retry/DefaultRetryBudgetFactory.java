package io.koraframework.resilient.retry;

import org.jspecify.annotations.Nullable;

/**
 * Default {@link RetryBudgetFactory} that builds a {@link KoraRetryBudget} from {@link RetryConfig.RetryBudgetConfig},
 * returning {@code null} when the budget is absent or disabled.
 */
public final class DefaultRetryBudgetFactory implements RetryBudgetFactory {

    @Nullable
    @Override
    public RetryBudget get(String name, RetryConfig config) {
        var retryBudget = config.retryBudget();
        if (retryBudget == null || !retryBudget.enabled()) {
            return null;
        }
        return new KoraRetryBudget(
            retryBudget.ratio(),
            retryBudget.tokensMax(),
            retryBudget.tokensInitial(),
            retryBudget.minTokensPerSecond()
        );
    }
}
