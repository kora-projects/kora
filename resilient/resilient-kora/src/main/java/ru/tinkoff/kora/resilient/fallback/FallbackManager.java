package ru.tinkoff.kora.resilient.fallback;


public interface FallbackManager {

    Fallback get(String name);
}
