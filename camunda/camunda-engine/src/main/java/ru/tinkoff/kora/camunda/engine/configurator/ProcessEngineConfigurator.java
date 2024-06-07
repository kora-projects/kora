
package ru.tinkoff.kora.camunda.engine.configurator;

import org.camunda.bpm.engine.ProcessEngine;

public interface ProcessEngineConfigurator {

    void setup(ProcessEngine engine) throws Exception;
}
