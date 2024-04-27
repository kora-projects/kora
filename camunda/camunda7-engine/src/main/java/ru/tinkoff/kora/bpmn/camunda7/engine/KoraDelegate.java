package ru.tinkoff.kora.bpmn.camunda7.engine;

import jakarta.annotation.Nonnull;
import org.camunda.bpm.engine.delegate.JavaDelegate;

public interface KoraDelegate extends JavaDelegate {

    @Nonnull
    String key();
}
