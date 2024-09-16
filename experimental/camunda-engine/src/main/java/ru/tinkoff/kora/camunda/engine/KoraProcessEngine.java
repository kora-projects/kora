package ru.tinkoff.kora.camunda.engine;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.ProcessEngineConfiguration;
import org.camunda.bpm.engine.ProcessEngines;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.application.graph.Wrapped;
import ru.tinkoff.kora.camunda.engine.configurator.ProcessEngineConfigurator;
import ru.tinkoff.kora.common.util.TimeUtils;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class KoraProcessEngine implements Lifecycle, Wrapped<ProcessEngine> {

    private static final Logger logger = LoggerFactory.getLogger(KoraProcessEngine.class);

    private final ProcessEngineConfiguration engineConfiguration;
    private final CamundaEngineConfig engineConfig;
    private final List<ProcessEngineConfigurator> camundaConfigurators;

    private volatile ProcessEngine processEngine;

    public KoraProcessEngine(ProcessEngineConfiguration engineConfiguration,
                             CamundaEngineConfig engineConfig,
                             List<ProcessEngineConfigurator> camundaConfigurators) {
        this.engineConfiguration = engineConfiguration;
        this.engineConfig = engineConfig;
        this.camundaConfigurators = camundaConfigurators;
    }

    @Override
    public void init() {
        if (engineConfig.parallelInitialization().enabled() && engineConfiguration instanceof KoraProcessEngineConfiguration) {
            logger.info("Camunda Engine parallel initialization enabled");

            logger.debug("Camunda Engine starting first stage...");
            final long started = TimeUtils.started();

            this.processEngine = engineConfiguration.buildProcessEngine();
            ProcessEngines.registerProcessEngine(processEngine);
            logger.info("Camunda Engine started first stage in {}", TimeUtils.tookForLogging(started));
        } else {
            logger.debug("Camunda Engine starting...");
            final long started = TimeUtils.started();

            this.processEngine = engineConfiguration.buildProcessEngine();
            ProcessEngines.registerProcessEngine(processEngine);
            logger.info("Camunda Engine started in {}", TimeUtils.tookForLogging(started));

            logger.debug("Camunda Engine configuring...");
            final long startedConfiguring = TimeUtils.started();

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
            logger.info("Camunda Engine configured in {}", TimeUtils.tookForLogging(startedConfiguring));
        }
    }

    @Override
    public void release() {
        logger.debug("Camunda Engine stopping...");
        final long started = TimeUtils.started();

        ProcessEngines.unregister(processEngine);
        processEngine.close();

        logger.info("Camunda Engine stopped in {}", TimeUtils.tookForLogging(started));
    }

    @Override
    public ProcessEngine value() {
        return processEngine;
    }
}
