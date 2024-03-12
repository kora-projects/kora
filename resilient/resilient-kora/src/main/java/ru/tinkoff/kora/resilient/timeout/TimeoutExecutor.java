package ru.tinkoff.kora.resilient.timeout;

import jakarta.annotation.Nonnull;
import ru.tinkoff.kora.application.graph.Lifecycle;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

public class TimeoutExecutor implements Lifecycle {

    private final ExecutorService executorService;

    public TimeoutExecutor(ExecutorService executorService) {
        this.executorService = executorService;
    }

    @Nonnull
    public ExecutorService executor() {
        return executorService;
    }

    @Override
    public void init() {
        // do nothing
    }

    @Override
    public void release() {
        if(executorService != null) {
            closeExecutorService(executorService);
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
