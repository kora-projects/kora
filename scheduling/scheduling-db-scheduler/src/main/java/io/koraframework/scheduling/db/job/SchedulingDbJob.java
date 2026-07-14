package io.koraframework.scheduling.db.job;

import com.github.kagkarlsson.scheduler.task.Task;

public interface SchedulingDbJob {

    Task<?> task();
}
