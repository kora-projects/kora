package ru.tinkoff.kora.camunda.engine.bpmn;

import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.jspecify.annotations.NonNull;

public interface KoraDelegate extends JavaDelegate {

    @NonNull
    default String key() {
        return getClass().getCanonicalName();
    }
}
