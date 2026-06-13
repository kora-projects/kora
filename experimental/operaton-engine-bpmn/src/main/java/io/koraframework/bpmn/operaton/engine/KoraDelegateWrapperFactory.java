package io.koraframework.bpmn.operaton.engine;

import org.operaton.bpm.engine.delegate.JavaDelegate;

public interface KoraDelegateWrapperFactory {

    JavaDelegate wrap(JavaDelegate delegate);
}
