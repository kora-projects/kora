package ru.tinkoff.kora.resilient.retry;

public interface RetryMetrics {

    void recordAttempt(String name, long delayInNanos);

    void recordExhaustedAttempts(String name, int totalAttempts);
}
