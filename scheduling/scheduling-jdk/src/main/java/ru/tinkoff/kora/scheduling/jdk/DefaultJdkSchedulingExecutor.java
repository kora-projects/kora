package ru.tinkoff.kora.scheduling.jdk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.common.util.TimeUtils;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class DefaultJdkSchedulingExecutor implements Lifecycle, JdkSchedulingExecutor {

    private static final Logger logger = LoggerFactory.getLogger(DefaultJdkSchedulingExecutor.class);

    private final ScheduledExecutorServiceConfig config;
    private volatile ScheduledThreadPoolExecutor service;

    public DefaultJdkSchedulingExecutor(ScheduledExecutorServiceConfig config) {
        this.config = config;
    }

    @Override
    public void init() {
        logger.debug("JdkSchedulingExecutor starting...");
        var started = System.nanoTime();

        var counter = new AtomicInteger();
        var service = new ScheduledThreadPoolExecutor(this.config.threads(), r -> {
            var name = "kora-scheduling-" + counter.incrementAndGet();
            var t = new Thread(r, name);
            t.setDaemon(false);
            return t;
        });
        service.setKeepAliveTime(1, TimeUnit.MINUTES);
        service.allowCoreThreadTimeOut(true);
        service.setRemoveOnCancelPolicy(true);
        this.service = service;

        logger.info("JdkSchedulingExecutor started in {}", TimeUtils.tookForLogging(started));
    }

    @Override
    public void release() {
        if (this.service != null) {
            logger.debug("JdkSchedulingExecutor stopping...");
            var started = System.nanoTime();
            if (!shutdownExecutorService(this.service, config.shutdownWait())) {
                logger.warn("JdkSchedulingExecutor failed completing graceful shutdown in {}", config.shutdownWait());
            }
            logger.info("JdkSchedulingExecutor stopped in {}", TimeUtils.tookForLogging(started));
        }
    }

    private boolean shutdownExecutorService(ExecutorService executorService, Duration shutdownAwait) {
        boolean terminated = executorService.isTerminated();
        if (!terminated) {
            executorService.shutdown();
            try {
                logger.debug("JdkSchedulingExecutor awaiting graceful shutdown...");
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
        logger.debug("Schedule with fixed delay: initialDelay={}, delay={}, unit={}, job={}", initialDelay, delay, timeUnit, job);
        return this.service.scheduleWithFixedDelay(job, initialDelay, delay, timeUnit);
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable job, long initialDelay, long period, TimeUnit timeUnit) {
        logger.debug("Schedule at fixed rate: initialDelay={}, period={}, unit={}, job={}", initialDelay, period, timeUnit, job);
        return this.service.scheduleAtFixedRate(job, initialDelay, period, timeUnit);
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable job, long delay, TimeUnit timeUnit) {
        logger.debug("Schedule at fixed rate: delay={}, unit={}, job={}", delay, timeUnit, job);
        return this.service.schedule(job, delay, timeUnit);
    }
}
