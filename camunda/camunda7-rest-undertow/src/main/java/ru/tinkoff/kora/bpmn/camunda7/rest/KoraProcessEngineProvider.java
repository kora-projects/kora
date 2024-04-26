package ru.tinkoff.kora.bpmn.camunda7.rest;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.ProcessEngines;
import org.camunda.bpm.engine.rest.spi.ProcessEngineProvider;

import java.util.Set;

public final class KoraProcessEngineProvider implements ProcessEngineProvider {

    @Override
    public ProcessEngine getDefaultProcessEngine() {
        return ProcessEngines.getDefaultProcessEngine();
    }

    @Override
    public ProcessEngine getProcessEngine(String name) {
        return ProcessEngines.getProcessEngine(name);
    }

    @Override
    public Set<String> getProcessEngineNames() {
        return ProcessEngines.getProcessEngines().keySet();
    }
}
