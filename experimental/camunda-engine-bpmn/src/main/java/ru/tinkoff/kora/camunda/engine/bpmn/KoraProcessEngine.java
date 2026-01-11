package ru.tinkoff.kora.camunda.engine.bpmn;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.ProcessEngineConfiguration;
import org.camunda.bpm.engine.ProcessEngines;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.application.graph.Wrapped;
import ru.tinkoff.kora.camunda.engine.bpmn.configurator.ProcessEngineConfigurator;
import ru.tinkoff.kora.common.util.TimeUtils;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class KoraProcessEngine implements Lifecycle, Wrapped<ProcessEngine> {

    private static final Logger logger = LoggerFactory.getLogger(KoraProcessEngine.class);

    private final ProcessEngineConfiguration engineConfiguration;
    private final CamundaEngineBpmnConfig engineConfig;
    private final List<ProcessEngineConfigurator> camundaConfigurators;

    private volatile ProcessEngine processEngine;

    public KoraProcessEngine(ProcessEngineConfiguration engineConfiguration,
                             CamundaEngineBpmnConfig engineConfig,
                             List<ProcessEngineConfigurator> camundaConfigurators) {
        this.engineConfig = engineConfig;
        this.engineConfiguration = engineConfiguration;
        this.camundaConfigurators = camundaConfigurators;
    }

    @Override
    public void init() {
        try {
            for (ProcessEngineConfigurator configurator : camundaConfigurators) {
                configurator.prepare(engineConfiguration);
            }

            if (engineConfig.parallelInitialization().enabled() && engineConfiguration instanceof KoraProcessEngineConfiguration) {
                logger.info("Camunda BPMN Engine parallel initialization enabled");

                logger.debug("Camunda BPMN Engine starting first stage...");
                final long started = TimeUtils.started();

                this.processEngine = engineConfiguration.buildProcessEngine();
                ProcessEngines.registerProcessEngine(processEngine);
                logger.info("Camunda BPMN Engine started first stage in {}", TimeUtils.tookForLogging(started));
            } else {
                logger.debug("Camunda BPMN Engine starting...");
                final long started = TimeUtils.started();

                this.processEngine = engineConfiguration.buildProcessEngine();
                ProcessEngines.registerProcessEngine(processEngine);
                logger.info("Camunda BPMN Engine started in {}", TimeUtils.tookForLogging(started));

                logger.debug("Camunda BPMN Engine configuring...");
                final long startedConfiguring = TimeUtils.started();

                var setups = new CompletableFuture<?>[camundaConfigurators.size()];
                for (var i = 0; i < camundaConfigurators.size(); i++) {
                    var camundaConfigurator = camundaConfigurators.get(i);
                    var future = new CompletableFuture<@Nullable Void>();
                    Thread.ofVirtual().name("camunda-process-engine-config-" + i).start(() -> {
                        try {
                            camundaConfigurator.setup(processEngine);
                            future.complete(null);
                        } catch (Throwable t) {
                            future.completeExceptionally(t);
                        }
                    });
                    setups[i] = future;
                }
                CompletableFuture.allOf(setups).join();
                logger.info("Camunda BPMN Engine configured in {}", TimeUtils.tookForLogging(startedConfiguring));
            }
        } catch (Exception e) {
            throw new RuntimeException("Camunda BPMN Engine failed to start, due to: " + e.getMessage(), e);
        }
    }

    @Override
    public void release() {
        logger.debug("Camunda BPMN Engine stopping...");
        final long started = TimeUtils.started();

        ProcessEngines.unregister(processEngine);
        processEngine.close();

        logger.info("Camunda BPMN Engine stopped in {}", TimeUtils.tookForLogging(started));
    }

    @Override
    public ProcessEngine value() {
        return processEngine;
    }
}
