package ru.tinkoff.kora.camunda.engine.bpmn;

import jakarta.annotation.Nonnull;
import jdk.jfr.Experimental;
import org.camunda.bpm.engine.delegate.JavaDelegate;

@Experimental
public interface KoraDelegate extends JavaDelegate {

    @Nonnull
    default String key() {
        return getClass().getCanonicalName();
    }
}
