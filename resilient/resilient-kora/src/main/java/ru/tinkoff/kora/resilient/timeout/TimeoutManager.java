package ru.tinkoff.kora.resilient.timeout;


/**
 * Manages state of all {@link Timeout} in system
 */
public interface TimeoutManager {

    Timeout get(String name);
}
