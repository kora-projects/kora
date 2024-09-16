package ru.tinkoff.kora.bpmn.camunda7.engine;

import org.camunda.bpm.engine.impl.ProcessEngineImpl;
import org.camunda.bpm.engine.impl.jobexecutor.DefaultJobExecutor;

public final class KoraJobExecutor extends DefaultJobExecutor {

    private final CamundaEngineConfig engineConfig;

    public KoraJobExecutor(CamundaEngineConfig engineConfig) {
        this.engineConfig = engineConfig;

        CamundaEngineConfig.CamundaJobExecutorConfig jobExecutorConfig = engineConfig.jobExecutor();
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
