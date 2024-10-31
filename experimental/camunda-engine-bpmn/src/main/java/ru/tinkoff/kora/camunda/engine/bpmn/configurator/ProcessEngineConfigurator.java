
package ru.tinkoff.kora.camunda.engine.bpmn.configurator;

import org.camunda.bpm.engine.ProcessEngine;
import org.jetbrains.annotations.ApiStatus.Experimental;

@Experimental
public interface ProcessEngineConfigurator {

    void setup(ProcessEngine engine) throws Exception;
}
