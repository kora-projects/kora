package io.koraframework.resilient.retry;

public interface RetryMetrics {

    void recordAttempt(String name, long delayInNanos);

    void recordExhaustedAttempts(String name, int totalAttempts);
}
