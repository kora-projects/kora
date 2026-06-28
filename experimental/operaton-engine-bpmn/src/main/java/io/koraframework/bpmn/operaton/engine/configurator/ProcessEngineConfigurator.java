
package io.koraframework.bpmn.operaton.engine.configurator;

import org.operaton.bpm.engine.ProcessEngine;
import org.operaton.bpm.engine.ProcessEngineConfiguration;

public interface ProcessEngineConfigurator {

    default void prepare(ProcessEngineConfiguration configuration) {

    }

    default void setup(ProcessEngine engine) throws Exception {

    }
}
