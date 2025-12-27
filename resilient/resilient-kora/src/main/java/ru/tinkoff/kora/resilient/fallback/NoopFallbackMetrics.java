package ru.tinkoff.kora.resilient.fallback;

final class NoopFallbackMetrics implements FallbackMetrics {

    static final NoopFallbackMetrics INSTANCE = new NoopFallbackMetrics();

    @Override
    public void recordExecute(String name, Throwable throwable) {
        // do nothing
    }
}
