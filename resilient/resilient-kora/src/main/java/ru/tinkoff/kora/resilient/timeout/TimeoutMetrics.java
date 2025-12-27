package ru.tinkoff.kora.resilient.timeout;

public interface TimeoutMetrics {

    void recordTimeout(String name, long timeoutInNanos);
}
