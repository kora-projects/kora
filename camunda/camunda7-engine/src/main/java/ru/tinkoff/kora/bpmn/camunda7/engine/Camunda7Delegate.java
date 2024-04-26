package ru.tinkoff.kora.bpmn.camunda7.engine;

import jakarta.annotation.Nonnull;
import org.camunda.bpm.engine.delegate.JavaDelegate;

public interface Camunda7Delegate extends JavaDelegate {

    @Nonnull
    String key();
}
