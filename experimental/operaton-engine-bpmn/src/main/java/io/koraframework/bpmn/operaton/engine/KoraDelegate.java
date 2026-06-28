package io.koraframework.bpmn.operaton.engine;

import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.jspecify.annotations.NonNull;

public interface KoraDelegate extends JavaDelegate {

    @NonNull
    default String key() {
        return getClass().getCanonicalName();
    }
}
