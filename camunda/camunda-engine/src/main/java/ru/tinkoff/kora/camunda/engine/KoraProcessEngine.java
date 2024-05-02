package ru.tinkoff.kora.camunda.engine;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.ProcessEngines;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.application.graph.Wrapped;

import java.time.Duration;

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
        logger.debug("Camunda Engine stopping...");
        final long started = System.nanoTime();

        ProcessEngines.unregister(processEngine);
        processEngine.close();

        logger.info("Camunda Engine stopped in {}", Duration.ofNanos(System.nanoTime() - started).toString().substring(2).toLowerCase());
    }

    @Override
    public ProcessEngine value() {
        return processEngine;
    }
}
