package ru.tinkoff.kora.scheduling.jdk;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public interface JdkSchedulingExecutor {

    /**
     * @see java.util.concurrent.ScheduledExecutorService#scheduleWithFixedDelay(Runnable, long, long, TimeUnit)
     */
    ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit timeUnit);

    /**
     * @see java.util.concurrent.ScheduledExecutorService#scheduleAtFixedRate(Runnable, long, long, TimeUnit)
     */
    ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelayMillis, long periodMillis, TimeUnit timeUnit);


    /**
     * @see java.util.concurrent.ScheduledExecutorService#schedule(Runnable, long, TimeUnit)
     */
    ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit timeUnit);
}
