package io.koraframework.scheduling.db;

import com.github.kagkarlsson.scheduler.SchedulerClient;
import com.github.kagkarlsson.scheduler.task.Task;
import com.github.kagkarlsson.scheduler.task.helper.RecurringTask;
import org.jspecify.annotations.Nullable;

public interface DbScheduledJob {

    Task<?> task();

    @Nullable
    RecurringTask<?> startupTask();

    default void scheduleOnStart(SchedulerClient scheduler) {}
}
