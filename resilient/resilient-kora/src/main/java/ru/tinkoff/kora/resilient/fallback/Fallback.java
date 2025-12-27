package ru.tinkoff.kora.resilient.fallback;


import java.util.function.Supplier;

public interface Fallback {

    boolean canFallback(Throwable throwable);

    void fallback(Runnable runnable, Runnable fallback);

    <T> T fallback(Supplier<T> supplier, Supplier<T> fallback);
}
