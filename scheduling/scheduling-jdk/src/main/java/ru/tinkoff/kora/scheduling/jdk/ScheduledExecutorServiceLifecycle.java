package ru.tinkoff.kora.scheduling.jdk;

import reactor.core.publisher.Mono;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.application.graph.Wrapped;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ScheduledExecutorServiceLifecycle implements Lifecycle, Wrapped<ScheduledExecutorService> {
    private final ScheduledExecutorServiceConfig config;
    private volatile ScheduledExecutorService service;

    public ScheduledExecutorServiceLifecycle(ScheduledExecutorServiceConfig config) {
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
    public ScheduledExecutorService value() {
        return this.service;
    }
}
