package io.koraframework.scheduling.jdk;

import io.koraframework.application.graph.All;
import io.koraframework.application.graph.Lifecycle;
import io.koraframework.application.graph.ValueOf;
import io.koraframework.common.util.TimeUtils;
import io.koraframework.scheduling.common.SchedulingJobConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class ThreadPoolSchedulingJdkExecutor implements Lifecycle, SchedulingJdkExecutor {

    private static final Logger logger = LoggerFactory.getLogger(ThreadPoolSchedulingJdkExecutor.class);

    private final SchedulingJdkConfig config;
    private final int jobsCount;

    private volatile ScheduledThreadPoolExecutor service;

    public ThreadPoolSchedulingJdkExecutor(All<ValueOf<SchedulingJobConfig>> jobConfigs, SchedulingJdkConfig config) {
        var count = 0;
        for (var _ : jobConfigs) {
            count++;
        }
        this.jobsCount = count;
        this.config = config;
    }

    @Override
    public void init() {
        logger.debug("SchedulingJdkExecutor starting...");
        var started = System.nanoTime();

        var counter = new AtomicInteger();
        var service = new ScheduledThreadPoolExecutor(jobsCount, runnable -> {
            var name = "kora-scheduler-" + counter.incrementAndGet();
            var thread = new Thread(runnable, name);
            thread.setDaemon(false);
            return thread;
        });
        service.setKeepAliveTime(30, TimeUnit.SECONDS);
        service.allowCoreThreadTimeOut(true);
        service.setRemoveOnCancelPolicy(true);
        this.service = service;

        logger.info("SchedulingJdkExecutor started in {}", TimeUtils.tookForLogging(started));
    }

    @Override
    public void release() {
        if (this.service != null) {
            logger.debug("SchedulingJdkExecutor stopping...");

            var started = System.nanoTime();
            if (!shutdownExecutorService(this.service, config.shutdownWait())) {
                logger.warn("SchedulingJdkExecutor failed completing graceful shutdown in {}", config.shutdownWait());
            }

            logger.info("SchedulingJdkExecutor stopped in {}", TimeUtils.tookForLogging(started));
        }
    }

    private boolean shutdownExecutorService(ExecutorService executorService, Duration shutdownAwait) {
        boolean terminated = executorService.isTerminated();
        if (!terminated) {
            executorService.shutdown();
            try {
                logger.debug("SchedulingJdkExecutor awaiting graceful shutdown...");
                terminated = executorService.awaitTermination(shutdownAwait.toMillis(), TimeUnit.MILLISECONDS);
                if (!terminated) {
                    executorService.shutdownNow();
                }
                return terminated;
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                return false;
            }
        } else {
            return true;
        }
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable job, long initialDelay, long delay, TimeUnit timeUnit) {
        logger.debug("Scheduling with fixed delay: initialDelay={}, delay={}, unit={}, job={}", initialDelay, delay, timeUnit, job);
        return this.service.scheduleWithFixedDelay(job, initialDelay, delay, timeUnit);
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable job, long initialDelay, long period, TimeUnit timeUnit) {
        logger.debug("Scheduling at fixed rate: initialDelay={}, period={}, unit={}, job={}", initialDelay, period, timeUnit, job);
        return this.service.scheduleAtFixedRate(job, initialDelay, period, timeUnit);
    }

    @Override
    public ScheduledFuture<?> scheduleOnce(Runnable job, long delay, TimeUnit timeUnit) {
        logger.debug("Scheduling once: delay={}, unit={}, job={}", delay, timeUnit, job);
        return this.service.schedule(job, delay, timeUnit);
    }
}
