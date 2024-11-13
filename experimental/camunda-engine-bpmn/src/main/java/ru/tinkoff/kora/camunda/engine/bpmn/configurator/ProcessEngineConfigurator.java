
package ru.tinkoff.kora.camunda.engine.bpmn.configurator;

import org.camunda.bpm.engine.ProcessEngine;

public interface ProcessEngineConfigurator {

    void setup(ProcessEngine engine) throws Exception;
}
