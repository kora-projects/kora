package ru.tinkoff.kora.resilient.retry;

public interface RetryManager {

    Retry get(String name);
}
