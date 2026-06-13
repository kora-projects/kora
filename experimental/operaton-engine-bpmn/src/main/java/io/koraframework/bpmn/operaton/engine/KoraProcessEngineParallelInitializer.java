package io.koraframework.bpmn.operaton.engine;

import io.koraframework.application.graph.Lifecycle;
import io.koraframework.bpmn.operaton.engine.configurator.ProcessEngineConfigurator;
import io.koraframework.common.util.TimeUtils;
import org.operaton.bpm.engine.ProcessEngine;
import org.operaton.bpm.engine.ProcessEngineConfiguration;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

public final class KoraProcessEngineParallelInitializer implements Lifecycle {

    private static final Logger logger = LoggerFactory.getLogger(KoraProcessEngineParallelInitializer.class);

    private final ProcessEngine processEngine;
    private final OperatonEngineBpmnConfig engineConfig;
    private final ProcessEngineConfiguration engineConfiguration;

    private final Iterable<ProcessEngineConfigurator> engineConfigurators;

    public KoraProcessEngineParallelInitializer(ProcessEngine processEngine,
                                                OperatonEngineBpmnConfig engineConfig,
                                                ProcessEngineConfiguration engineConfiguration,
                                                Iterable<ProcessEngineConfigurator> engineConfigurators) {
        this.processEngine = processEngine;
        this.engineConfig = engineConfig;
        this.engineConfiguration = engineConfiguration;
        this.engineConfigurators = engineConfigurators;
    }

    @Override
    public void init() {
        if (engineConfig.parallelInitialization().enabled() && engineConfiguration instanceof KoraProcessEngineConfiguration) {
            logger.debug("Operaton BPMN Engine parallel configuring...");
            final long started = TimeUtils.started();
            var configurators = new ArrayList<ProcessEngineConfigurator>();
            for (var engineConfigurator : this.engineConfigurators) {
                configurators.add(engineConfigurator);
            }
            var setups = new CompletableFuture<?>[configurators.size()];
            for (var i = 0; i < configurators.size(); i++) {
                var engineConfigurator = configurators.get(i);
                var future = new CompletableFuture<@Nullable Void>();
                Thread.ofVirtual().name("operaton-process-engine-config-" + i).start(() -> {
                    try {
                        engineConfigurator.setup(processEngine);
                        future.complete(null);
                    } catch (Throwable t) {
                        future.completeExceptionally(t);
                    }
                });
                setups[i] = future;
            }
            CompletableFuture.allOf(setups).join();
            logger.info("Operaton BPMN Engine parallel configured in {}", TimeUtils.tookForLogging(started));
        }
    }

    @Override
    public void release() {
        // do nothing
    }
}
