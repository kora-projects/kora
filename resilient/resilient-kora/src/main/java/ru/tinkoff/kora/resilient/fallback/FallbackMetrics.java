package ru.tinkoff.kora.resilient.fallback;

public interface FallbackMetrics {

    void recordExecute(String name, Throwable throwable);
}
