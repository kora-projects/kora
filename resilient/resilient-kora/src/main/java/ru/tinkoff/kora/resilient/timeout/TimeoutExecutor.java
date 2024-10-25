package ru.tinkoff.kora.resilient.timeout;

import jakarta.annotation.Nonnull;
import ru.tinkoff.kora.application.graph.Lifecycle;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

public class TimeoutExecutor implements Lifecycle {

    private final Executor executorService;

    public TimeoutExecutor(Executor executorService) {
        this.executorService = executorService;
    }

    @Nonnull
    public Executor executor() {
        return executorService;
    }

    @Override
    public void init() {
        // do nothing
    }

    @Override
    public void release() {
        if (executorService instanceof ExecutorService es) {
            closeExecutorService(es);
        }
    }

    private static void closeExecutorService(ExecutorService executorService) {
        boolean terminated = executorService.isTerminated();
        if (!terminated) {
            executorService.shutdown();
            boolean interrupted = false;
            while (!terminated) {
                try {
                    terminated = executorService.awaitTermination(30, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    if (!interrupted) {
                        executorService.shutdownNow();
                        interrupted = true;
                    }
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
