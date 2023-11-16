package ru.tinkoff.kora.scheduling.jdk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.application.graph.Lifecycle;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class DefaultJdkSchedulingExecutor implements Lifecycle, JdkSchedulingExecutor {
    private static final Logger log = LoggerFactory.getLogger(DefaultJdkSchedulingExecutor.class);

    private final ScheduledExecutorServiceConfig config;
    private volatile ScheduledThreadPoolExecutor service;

    public DefaultJdkSchedulingExecutor(ScheduledExecutorServiceConfig config) {
        this.config = config;
    }

    @Override
    public void init() {
        var counter = new AtomicInteger();
        this.service = (ScheduledThreadPoolExecutor) Executors.newScheduledThreadPool(config.threads(), r -> {
            var name = "kora-scheduling-" + counter.incrementAndGet();
            var t = new Thread(r, name);
            t.setDaemon(false);
            return t;
        });
        this.service.setRemoveOnCancelPolicy(true);
    }

    @Override
    public void release() throws InterruptedException {
        this.service.shutdownNow();
        this.service.awaitTermination(10, TimeUnit.SECONDS);
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable job, long initialDelay, long delay, TimeUnit timeUnit) {
        log.debug("Schedule with fixed delay: initialDelay={}, delay={}, unit={}, job={}", initialDelay, delay, timeUnit, job);
        return this.service.scheduleWithFixedDelay(job, initialDelay, delay, timeUnit);
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable job, long initialDelay, long period, TimeUnit timeUnit) {
        log.debug("Schedule at fixed rate: initialDelay={}, period={}, unit={}, job={}", initialDelay, period, timeUnit, job);
        return this.service.scheduleAtFixedRate(job, initialDelay, period, timeUnit);
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable job, long delay, TimeUnit timeUnit) {
        log.debug("Schedule at fixed rate: delay={}, unit={}, job={}", delay, timeUnit, job);
        return this.service.schedule(job, delay, timeUnit);
    }
}
