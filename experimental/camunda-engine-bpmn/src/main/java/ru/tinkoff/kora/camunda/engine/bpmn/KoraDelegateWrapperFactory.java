package ru.tinkoff.kora.camunda.engine.bpmn;

import org.camunda.bpm.engine.delegate.JavaDelegate;

public interface KoraDelegateWrapperFactory {

    JavaDelegate wrap(JavaDelegate delegate);
}
