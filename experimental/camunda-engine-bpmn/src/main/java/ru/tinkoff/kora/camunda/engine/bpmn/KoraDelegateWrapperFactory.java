package ru.tinkoff.kora.camunda.engine.bpmn;

import jdk.jfr.Experimental;
import org.camunda.bpm.engine.delegate.JavaDelegate;

@Experimental
public interface KoraDelegateWrapperFactory {

    JavaDelegate wrap(JavaDelegate delegate);
}
