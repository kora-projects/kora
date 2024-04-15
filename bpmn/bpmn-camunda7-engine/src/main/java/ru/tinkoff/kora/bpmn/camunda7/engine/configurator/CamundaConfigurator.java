
package ru.tinkoff.kora.bpmn.camunda7.engine.configurator;

import org.camunda.bpm.engine.ProcessEngine;

public interface CamundaConfigurator {

    void setup(ProcessEngine processEngine) throws Exception;
}
