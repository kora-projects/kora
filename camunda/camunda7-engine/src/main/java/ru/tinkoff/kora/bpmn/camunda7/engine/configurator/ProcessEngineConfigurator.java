
package ru.tinkoff.kora.bpmn.camunda7.engine.configurator;

import org.camunda.bpm.engine.ProcessEngine;

public interface ProcessEngineConfigurator {

    void setup(ProcessEngine engine) throws Exception;
}
