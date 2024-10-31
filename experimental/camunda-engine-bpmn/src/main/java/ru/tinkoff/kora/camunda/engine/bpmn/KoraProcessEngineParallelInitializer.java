package ru.tinkoff.kora.camunda.engine.bpmn;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.ProcessEngineConfiguration;
import org.jetbrains.annotations.ApiStatus.Experimental;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.camunda.engine.bpmn.configurator.ProcessEngineConfigurator;
import ru.tinkoff.kora.common.util.TimeUtils;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Experimental
public final class KoraProcessEngineParallelInitializer implements Lifecycle {

    private static final Logger logger = LoggerFactory.getLogger(KoraProcessEngineParallelInitializer.class);

    private final ProcessEngine processEngine;
    private final CamundaEngineBpmnConfig camundaEngineConfig;
    private final ProcessEngineConfiguration engineConfiguration;
    private final List<ProcessEngineConfigurator> camundaConfigurators;

    public KoraProcessEngineParallelInitializer(ProcessEngine processEngine,
                                                CamundaEngineBpmnConfig camundaEngineConfig,
                                                ProcessEngineConfiguration engineConfiguration,
                                                List<ProcessEngineConfigurator> camundaConfigurators) {
        this.processEngine = processEngine;
        this.camundaEngineConfig = camundaEngineConfig;
        this.engineConfiguration = engineConfiguration;
        this.camundaConfigurators = camundaConfigurators;
    }

    @Override
    public void init() {
        if (camundaEngineConfig.parallelInitialization().enabled() && engineConfiguration instanceof KoraProcessEngineConfiguration) {
            logger.debug("Camunda BPMN Engine parallel configuring...");
            final long started = TimeUtils.started();

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
            logger.info("Camunda BPMN Engine parallel configured in {}", TimeUtils.tookForLogging(started));
        }
    }

    @Override
    public void release() {
        // do nothing
    }
}
