package io.koraframework.bpmn.operaton.rest;

import org.operaton.bpm.engine.ProcessEngine;
import org.operaton.bpm.engine.ProcessEngineProvider;
import org.operaton.bpm.engine.ProcessEngines;

public final class KoraProcessEngineProvider implements ProcessEngineProvider {

    @Override
    public ProcessEngine getProcessEngine() {
        return ProcessEngines.getDefaultProcessEngine();
    }
}
