package io.koraframework.bpmn.operaton.engine;

import org.operaton.bpm.engine.impl.ProcessEngineImpl;
import org.operaton.bpm.engine.impl.jobexecutor.JobExecutor;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

public final class KoraVirtualThreadJobExecutor extends JobExecutor {

    private final Executor executor;
    private final OperatonEngineBpmnConfig engineConfig;

    public KoraVirtualThreadJobExecutor(OperatonEngineBpmnConfig engineConfig) {
        var factory = Thread.ofVirtual().name("operaton-job-executor-", 1).factory();
        this.executor = r -> factory.newThread(r).start();
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
