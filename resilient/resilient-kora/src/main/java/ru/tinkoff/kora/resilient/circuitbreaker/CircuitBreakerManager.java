package ru.tinkoff.kora.resilient.circuitbreaker;


/**
 * Manages state of all {@link CircuitBreaker} in system
 */
public interface CircuitBreakerManager {

    CircuitBreaker get(String name);
}
