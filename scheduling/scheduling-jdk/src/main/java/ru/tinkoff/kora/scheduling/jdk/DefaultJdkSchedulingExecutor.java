package ru.tinkoff.kora.scheduling.jdk;

import reactor.core.publisher.Mono;
import ru.tinkoff.kora.application.graph.Lifecycle;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class DefaultJdkSchedulingExecutor implements Lifecycle, JdkSchedulingExecutor {
    private final ScheduledExecutorServiceConfig config;
    private volatile ScheduledExecutorService service;

    public DefaultJdkSchedulingExecutor(ScheduledExecutorServiceConfig config) {
        this.config = config;
    }

    @Override
    public Mono<?> init() {
        return Mono.fromRunnable(() -> {
            var counter = new AtomicInteger();
            this.service = Executors.newScheduledThreadPool(config.threads(), r -> {
                var name = "kora-scheduling-" + counter.incrementAndGet();
                var t = new Thread(r, name);
                t.setDaemon(false);
                return t;
            });
        });
    }

    @Override
    public Mono<?> release() {
        return Mono.fromRunnable(() -> {
            this.service.shutdownNow();
            try {
                this.service.awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit milliseconds) {
        return this.service.scheduleWithFixedDelay(command, initialDelay, delay, milliseconds);
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelayMillis, long periodMillis, TimeUnit milliseconds) {
        return this.service.scheduleAtFixedRate(command, initialDelayMillis, periodMillis, milliseconds);
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit timeUnit) {
        return this.service.schedule(command, delay, timeUnit);
    }
}
