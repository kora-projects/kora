package io.koraframework.bpmn.operaton.engine;

import io.koraframework.application.graph.Lifecycle;
import io.koraframework.application.graph.Wrapped;
import io.koraframework.bpmn.operaton.engine.configurator.ProcessEngineConfigurator;
import io.koraframework.common.util.TimeUtils;
import org.jspecify.annotations.Nullable;
import org.operaton.bpm.engine.ProcessEngine;
import org.operaton.bpm.engine.ProcessEngineConfiguration;
import org.operaton.bpm.engine.ProcessEngines;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

public final class KoraProcessEngine implements Lifecycle, Wrapped<ProcessEngine> {

    private static final Logger logger = LoggerFactory.getLogger(KoraProcessEngine.class);

    private final ProcessEngineConfiguration engineConfiguration;
    private final OperatonEngineBpmnConfig engineConfig;
    private final Iterable<ProcessEngineConfigurator> engineConfigurators;

    private volatile ProcessEngine processEngine;

    public KoraProcessEngine(ProcessEngineConfiguration engineConfiguration,
                             OperatonEngineBpmnConfig engineConfig,
                             Iterable<ProcessEngineConfigurator> engineConfigurators) {
        this.engineConfig = engineConfig;
        this.engineConfiguration = engineConfiguration;
        this.engineConfigurators = engineConfigurators;
    }

    @Override
    public void init() {
        try {
            for (ProcessEngineConfigurator configurator : engineConfigurators) {
                configurator.prepare(engineConfiguration);
            }

            if (engineConfig.parallelInitialization().enabled() && engineConfiguration instanceof KoraProcessEngineConfiguration) {
                logger.info("Operaton BPMN Engine parallel initialization enabled");

                logger.debug("Operaton BPMN Engine starting first stage...");
                final long started = TimeUtils.started();

                this.processEngine = engineConfiguration.buildProcessEngine();
                ProcessEngines.registerProcessEngine(processEngine);
                logger.info("Operaton BPMN Engine started first stage in {}", TimeUtils.tookForLogging(started));
            } else {
                logger.debug("Operaton BPMN Engine starting...");
                final long started = TimeUtils.started();

                this.processEngine = engineConfiguration.buildProcessEngine();
                ProcessEngines.registerProcessEngine(processEngine);
                logger.info("Operaton BPMN Engine started in {}", TimeUtils.tookForLogging(started));

                logger.debug("Operaton BPMN Engine configuring...");
                final long startedConfiguring = TimeUtils.started();

                var configurators = new ArrayList<ProcessEngineConfigurator>();
                for (var configurator : this.engineConfigurators) {
                    configurators.add(configurator);
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
                logger.info("Operaton BPMN Engine configured in {}", TimeUtils.tookForLogging(startedConfiguring));
            }
        } catch (Exception e) {
            throw new RuntimeException("Operaton BPMN Engine failed to start, due to: " + e.getMessage(), e);
        }
    }

    @Override
    public void release() {
        if (processEngine != null) {
            logger.debug("Operaton BPMN Engine stopping...");
            final long started = TimeUtils.started();

            ProcessEngines.unregister(processEngine);
            processEngine.close();

            logger.info("Operaton BPMN Engine stopped in {}", TimeUtils.tookForLogging(started));
        }
    }

    @Override
    public ProcessEngine value() {
        return processEngine;
    }
}
