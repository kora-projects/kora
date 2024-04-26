package ru.tinkoff.kora.bpmn.camunda7.engine;

import org.camunda.bpm.engine.impl.ProcessEngineImpl;
import org.camunda.bpm.engine.impl.jobexecutor.DefaultJobExecutor;

public final class KoraJobExecutor extends DefaultJobExecutor {

    private final Camunda7EngineConfig engineConfig;

    public KoraJobExecutor(Camunda7EngineConfig engineConfig) {
        this.engineConfig = engineConfig;

        Camunda7EngineConfig.JobExecutorConfig jobExecutorConfig = engineConfig.jobExecutor();
        if (jobExecutorConfig.queueSize() != null) {
            setQueueSize(jobExecutorConfig.queueSize());
        }
        if (jobExecutorConfig.corePoolSize() != null) {
            setCorePoolSize(jobExecutorConfig.corePoolSize());
        }
        if (jobExecutorConfig.maxPoolSize() != null) {
            setMaxPoolSize(jobExecutorConfig.maxPoolSize());
        }
        if (jobExecutorConfig.maxJobsPerAcquisition() != null) {
            setMaxJobsPerAcquisition(jobExecutorConfig.maxJobsPerAcquisition());
        }
    }

    @Override
    public synchronized void registerProcessEngine(ProcessEngineImpl processEngine) {
        if (engineConfig.twoStage()) {
            processEngines.add(processEngine);
            // JobExecutor is started in ParallelInitializationService
        } else {
            super.registerProcessEngine(processEngine);
        }
    }
}
