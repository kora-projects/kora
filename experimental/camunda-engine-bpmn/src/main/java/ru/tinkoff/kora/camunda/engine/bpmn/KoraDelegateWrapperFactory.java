package ru.tinkoff.kora.camunda.engine.bpmn;

import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.jetbrains.annotations.ApiStatus.Experimental;

@Experimental
public interface KoraDelegateWrapperFactory {

    JavaDelegate wrap(JavaDelegate delegate);
}
