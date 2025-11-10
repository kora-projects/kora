package ru.tinkoff.kora.camunda.engine.bpmn;

import org.camunda.bpm.engine.impl.ProcessEngineImpl;
import org.camunda.bpm.engine.impl.jobexecutor.DefaultJobExecutor;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class KoraThreadPoolJobExecutor extends DefaultJobExecutor {

    private final CamundaEngineBpmnConfig engineConfig;

    public KoraThreadPoolJobExecutor(CamundaEngineBpmnConfig engineConfig) {
        this.engineConfig = engineConfig;

        var jobExecutorConfig = engineConfig.jobExecutor();
        setQueueSize(jobExecutorConfig.queueSize());
        setCorePoolSize(jobExecutorConfig.corePoolSize());
        setMaxPoolSize(jobExecutorConfig.maxPoolSize());
        setMaxJobsPerAcquisition(jobExecutorConfig.maxJobsPerAcquisition());
    }

    @Override
    protected void startExecutingJobs() {
        if (threadPoolExecutor == null || threadPoolExecutor.isShutdown()) {
            final BlockingQueue<Runnable> threadPoolQueue = new ArrayBlockingQueue<Runnable>(queueSize);
            final AtomicInteger threadNumber = new AtomicInteger(1);
            threadPoolExecutor = new ThreadPoolExecutor(corePoolSize, maxPoolSize, 0L, TimeUnit.MILLISECONDS, threadPoolQueue,
                r -> {
                    var name = "camunda-worker-" + threadNumber.incrementAndGet();
                    Thread t = new Thread(r, name);
                    if (t.isDaemon())
                        t.setDaemon(false);
                    if (t.getPriority() != Thread.NORM_PRIORITY)
                        t.setPriority(Thread.NORM_PRIORITY);
                    return t;
                });
            threadPoolExecutor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        }

        super.startExecutingJobs();
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
