package ru.tinkoff.kora.camunda.engine.bpmn;

import org.camunda.bpm.engine.impl.ProcessEngineImpl;
import org.camunda.bpm.engine.impl.jobexecutor.JobExecutor;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

public final class KoraVirtualThreadJobExecutor extends JobExecutor {

    private final Executor executor;
    private final CamundaEngineBpmnConfig engineConfig;

    public KoraVirtualThreadJobExecutor(Executor executor, CamundaEngineBpmnConfig engineConfig) {
        this.executor = executor;
        this.engineConfig = engineConfig;
    }

    protected void startExecutingJobs() {
        startJobAcquisitionThread();
    }

    protected void stopExecutingJobs() {
        stopJobAcquisitionThread();
    }

    public void executeJobs(List<String> jobIds, ProcessEngineImpl processEngine) {
        try {
            executor.execute(getExecuteJobsRunnable(jobIds, processEngine));
        } catch (RejectedExecutionException e) {
            logRejectedExecution(processEngine, jobIds.size());
            rejectedJobsHandler.jobsRejected(jobIds, processEngine, this);
        }
    }

    @Override
    public synchronized void registerProcessEngine(ProcessEngineImpl processEngine) {
        if (engineConfig.parallelInitialization().enabled()) {
            processEngines.add(processEngine);
            // JobExecutor is started in ParallelInitializationService
        } else {
            super.registerProcessEngine(processEngine);
        }
    }
}
