package ru.tinkoff.kora.bpmn.camunda7.engine;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.ProcessEngines;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.application.graph.Wrapped;
import ru.tinkoff.kora.bpmn.camunda7.engine.configurator.ProcessEngineConfigurator;

import java.time.Duration;
import java.util.List;

public final class KoraProcessEngine implements Lifecycle, Wrapped<ProcessEngine> {

    private static final Logger logger = LoggerFactory.getLogger(KoraProcessEngine.class);

    private final KoraProcessEngineConfiguration processEngineConfiguration;

    private volatile ProcessEngine processEngine;

    public KoraProcessEngine(KoraProcessEngineConfiguration processEngineConfiguration) {
        this.processEngineConfiguration = processEngineConfiguration;
    }

    @Override
    public void init() {
        logger.debug("Camunda Engine starting...");
        final long started = System.nanoTime();

        this.processEngine = processEngineConfiguration.buildProcessEngine();
        ProcessEngines.registerProcessEngine(processEngine);

        logger.info("Camunda Engine started in {}", Duration.ofNanos(System.nanoTime() - started).toString().substring(2).toLowerCase());
    }

    @Override
    public void release() {
        ProcessEngines.unregister(processEngine);
        processEngine.close();
    }

    @Override
    public ProcessEngine value() {
        return processEngine;
    }
}
