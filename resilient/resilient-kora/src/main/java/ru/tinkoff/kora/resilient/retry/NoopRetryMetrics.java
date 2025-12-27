package ru.tinkoff.kora.resilient.retry;


final class NoopRetryMetrics implements RetryMetrics {

    @Override
    public void recordAttempt(String name, long delayInNanos) {
        // do nothing
    }

    @Override
    public void recordExhaustedAttempts(String name, int totalAttempts) {
        // do nothing
    }
}
