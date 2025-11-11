package ru.tinkoff.kora.camunda.engine.bpmn;

import org.camunda.bpm.engine.impl.ProcessEngineImpl;
import org.camunda.bpm.engine.impl.jobexecutor.JobExecutor;
import ru.tinkoff.kora.application.graph.internal.loom.VirtualThreadExecutorHolder;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

public final class KoraVirtualThreadJobExecutor extends JobExecutor {

    private final Executor executor;
    private final CamundaEngineBpmnConfig engineConfig;

    public KoraVirtualThreadJobExecutor(CamundaEngineBpmnConfig engineConfig) {
        if (VirtualThreadExecutorHolder.status() != VirtualThreadExecutorHolder.VirtualThreadStatus.ENABLED) {
            throw new IllegalStateException("Camunda BPMN Engine starting failed cause Virtual Threads are not available but enabled in configuration, please check that you are using Java 21+ or disable Virtual Threads for Camunda BPMN Engine in configuration.");
        }

        this.executor = VirtualThreadExecutorHolder.executor();
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
