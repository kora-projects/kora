
package ru.tinkoff.kora.camunda.engine.bpmn.configurator;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.ProcessEngineConfiguration;

public interface ProcessEngineConfigurator {

    default void prepare(ProcessEngineConfiguration configuration) {

    }

    default void setup(ProcessEngine engine) throws Exception {

    }
}
