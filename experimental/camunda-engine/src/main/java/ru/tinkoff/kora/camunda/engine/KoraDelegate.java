package ru.tinkoff.kora.camunda.engine;

import jakarta.annotation.Nonnull;
import org.camunda.bpm.engine.delegate.JavaDelegate;

public interface KoraDelegate extends JavaDelegate {

    @Nonnull
    default String key() {
        return getClass().getCanonicalName();
    }
}
