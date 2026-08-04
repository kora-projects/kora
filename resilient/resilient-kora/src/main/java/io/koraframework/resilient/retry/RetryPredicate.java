package io.koraframework.resilient.retry;

/**
 * Configures behavior of {@link Retry} on whenever exception should count as failre or not
 */
@FunctionalInterface
public interface RetryPredicate {

    /**
     * @param throwable to test
     * @return when True than throwable is registered as failure
     */
    boolean isRetryFailure(Throwable throwable);
}
