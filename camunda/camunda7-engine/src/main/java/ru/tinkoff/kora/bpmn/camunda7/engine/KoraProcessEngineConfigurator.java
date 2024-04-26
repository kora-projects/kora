package ru.tinkoff.kora.bpmn.camunda7.engine;

import org.camunda.bpm.engine.ProcessEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.bpmn.camunda7.engine.configurator.ProcessEngineConfigurator;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class KoraProcessEngineConfigurator implements Lifecycle {

    private static final Logger logger = LoggerFactory.getLogger(KoraProcessEngineConfigurator.class);

    private final ProcessEngine processEngine;
    private final List<ProcessEngineConfigurator> camundaConfigurators;

    public KoraProcessEngineConfigurator(ProcessEngine processEngine,
                                         List<ProcessEngineConfigurator> camundaConfigurators) {
        this.processEngine = processEngine;
        this.camundaConfigurators = camundaConfigurators;
    }

    @Override
    public void init() {
        logger.debug("Camunda Engine configuring...");
        final long started = System.nanoTime();

        var setups = camundaConfigurators.stream()
            .map(c -> CompletableFuture.runAsync(() -> {
                try {
                    c.setup(processEngine);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }))
            .toArray(CompletableFuture[]::new);

        CompletableFuture.allOf(setups).join();
        logger.info("Camunda Engine configured in {}", Duration.ofNanos(System.nanoTime() - started).toString().substring(2).toLowerCase());
    }

    @Override
    public void release() {
        // do nothing
    }
}
