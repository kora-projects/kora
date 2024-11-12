
package ru.tinkoff.kora.camunda.engine.bpmn.configurator;

import jdk.jfr.Experimental;
import org.camunda.bpm.engine.ProcessEngine;

@Experimental
public interface ProcessEngineConfigurator {

    void setup(ProcessEngine engine) throws Exception;
}
