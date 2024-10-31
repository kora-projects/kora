package ru.tinkoff.kora.camunda.engine.bpmn;

import jakarta.annotation.Nonnull;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.jetbrains.annotations.ApiStatus.Experimental;

@Experimental
public interface KoraDelegate extends JavaDelegate {

    @Nonnull
    default String key() {
        return getClass().getCanonicalName();
    }
}
