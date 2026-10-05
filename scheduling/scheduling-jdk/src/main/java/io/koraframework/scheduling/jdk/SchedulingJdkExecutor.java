package io.koraframework.scheduling.jdk;

import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public interface SchedulingJdkExecutor {

    /**
     * @see java.util.concurrent.ScheduledExecutorService#scheduleWithFixedDelay(Runnable, long, long, TimeUnit)
     */
    ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit timeUnit);

    /**
     * @see java.util.concurrent.ScheduledExecutorService#scheduleAtFixedRate(Runnable, long, long, TimeUnit)
     */
    ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit timeUnit);

    /**
     * @see java.util.concurrent.ScheduledExecutorService#schedule(Runnable, long, TimeUnit)
     */
    ScheduledFuture<?> scheduleOnce(Runnable command, long delay, TimeUnit timeUnit);

    /**
     * How long a job waits on release for its running execution to finish before interrupting it.
     *
     * @return {@code null} if jobs neither wait for nor interrupt their running execution on release
     */
    default @Nullable Duration shutdownWait() {
        return null;
    }
}
