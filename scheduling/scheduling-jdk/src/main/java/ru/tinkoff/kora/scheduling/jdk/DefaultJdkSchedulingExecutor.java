package ru.tinkoff.kora.scheduling.jdk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.common.util.TimeUtils;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

public final class DefaultJdkSchedulingExecutor implements Lifecycle, JdkSchedulingExecutor {

    private static final Logger logger = LoggerFactory.getLogger(DefaultJdkSchedulingExecutor.class);
    private static final AtomicReferenceFieldUpdater<DefaultJdkSchedulingExecutor, ScheduledThreadPoolExecutor> SERVICE = AtomicReferenceFieldUpdater.newUpdater(
        DefaultJdkSchedulingExecutor.class,
        ScheduledThreadPoolExecutor.class,
        "service"
    );

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
        var service = new ScheduledThreadPoolExecutor(0, r -> {
            var name = "kora-scheduling-" + counter.incrementAndGet();
            var t = new Thread(r, name);
            t.setDaemon(false);
            return t;
        });
        service.setMaximumPoolSize(this.config.threads());
        service.setKeepAliveTime(1, TimeUnit.MINUTES);
        service.allowCoreThreadTimeOut(true);
        service.setRemoveOnCancelPolicy(true);
        if (!SERVICE.compareAndSet(this, null, service)) {
            service.shutdownNow();
        }

        logger.info("JdkSchedulingExecutor started in {}", TimeUtils.tookForLogging(started));
    }

    @Override
    public void release() throws InterruptedException {

        var service = SERVICE.getAndSet(this, null);
        if (service != null) {
            logger.debug("JdkSchedulingExecutor stopping...");
            var started = System.nanoTime();

            service.shutdownNow();
            service.awaitTermination(10, TimeUnit.SECONDS);
            logger.info("JdkSchedulingExecutor stopped in {}", TimeUtils.tookForLogging(started));
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
