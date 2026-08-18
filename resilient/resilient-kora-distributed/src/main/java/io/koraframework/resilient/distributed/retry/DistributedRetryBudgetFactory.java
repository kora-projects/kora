package io.koraframework.resilient.distributed.retry;

import io.koraframework.resilient.retry.RetryBudget;
import io.koraframework.resilient.retry.RetryBudgetFactory;
import io.koraframework.resilient.retry.RetryConfig;
import org.jspecify.annotations.Nullable;

import java.time.Duration;

/**
 * {@link RetryBudgetFactory} that produces {@link DistributedRetryBudget} instances backed by a shared
 * {@link DistributedRetryBudgetClient}, so a retry's budget is enforced across all application instances.
 *
 * <p>Plug it into a specific retry by registering it tagged with that retry's contract type, or as the global override:
 *
 * <pre>{@code
 * @Tag(MyRetry.class)
 * default RetryBudgetFactory myRetryBudget(DistributedRetryBudgetFactory distributed) {
 *     return distributed;
 * }
 * }</pre>
 */
public final class DistributedRetryBudgetFactory implements RetryBudgetFactory {

    private static final String DEFAULT_KEY_PREFIX = "kora:retrybudget";
    private static final Duration DEFAULT_BUCKET_TTL = Duration.ofMinutes(10);

    private final DistributedRetryBudgetClient client;
    private final String keyPrefix;
    private final long ttlMillis;

    public DistributedRetryBudgetFactory(DistributedRetryBudgetClient client) {
        this(client, DEFAULT_KEY_PREFIX, DEFAULT_BUCKET_TTL);
    }

    public DistributedRetryBudgetFactory(DistributedRetryBudgetClient client, String keyPrefix, Duration bucketTtl) {
        this.client = client;
        this.keyPrefix = keyPrefix;
        this.ttlMillis = bucketTtl.toMillis();
    }

    @Nullable
    @Override
    public RetryBudget get(String name, RetryConfig config) {
        final RetryConfig.RetryBudgetConfig budget = config.retryBudget();
        if (budget == null || !budget.enabled()) {
            return null;
        }
        final String key = keyPrefix + ':' + name;
        return new DistributedRetryBudget(
            key,
            budget.tokensMax(),
            budget.tokensInitial(),
            budget.ratio(),
            ttlMillis,
            client
        );
    }
}
