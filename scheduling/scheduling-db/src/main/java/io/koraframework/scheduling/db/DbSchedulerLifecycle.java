package io.koraframework.scheduling.db;

import com.github.kagkarlsson.scheduler.Scheduler;
import com.github.kagkarlsson.scheduler.task.Task;
import com.github.kagkarlsson.scheduler.task.helper.RecurringTask;
import io.koraframework.application.graph.All;
import io.koraframework.application.graph.Lifecycle;
import io.koraframework.application.graph.ValueOf;
import io.koraframework.common.util.TimeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.ArrayList;

public final class DbSchedulerLifecycle implements Lifecycle {

    private static final Logger logger = LoggerFactory.getLogger(DbSchedulerLifecycle.class);

    private final DataSource dataSource;
    private final SchedulingDbConfig config;
    private final All<ValueOf<DbScheduledJob>> jobs;

    private volatile Scheduler scheduler;

    public DbSchedulerLifecycle(DataSource dataSource, SchedulingDbConfig config, All<ValueOf<DbScheduledJob>> jobs) {
        this.dataSource = dataSource;
        this.config = config;
        this.jobs = jobs;
    }

    @Override
    public void init() throws Exception {
        logger.debug("DbScheduler starting...");
        var started = TimeUtils.started();

        if (this.config.initializeTable()) {
            DbSchedulerTableInitializer.initialize(this.dataSource, this.config.tableName());
        }

        var tasks = new ArrayList<Task<?>>();
        var startupTasks = new ArrayList<RecurringTask<?>>();
        var resolvedJobs = new ArrayList<DbScheduledJob>();
        for (var value : this.jobs) {
            var job = value.get();
            resolvedJobs.add(job);
            tasks.add(job.task());
            var startupTask = job.startupTask();
            if (startupTask != null) {
                startupTasks.add(startupTask);
            }
        }
        var threads = Math.max(1, resolvedJobs.size());

        var scheduler = Scheduler.create(this.dataSource, tasks)
            .threads(threads)
            .pollingInterval(this.config.pollingInterval())
            .shutdownMaxWait(this.config.shutdownWait())
            .tableName(this.config.tableName())
            .startTasks(startupTasks)
            .build();

        scheduler.start();
        for (var job : resolvedJobs) {
            job.scheduleOnStart(scheduler);
        }
        this.scheduler = scheduler;

        logger.info("DbScheduler started in {}", TimeUtils.tookForLogging(started));
    }

    @Override
    public void release() {
        var scheduler = this.scheduler;
        if (scheduler == null) {
            return;
        }

        logger.debug("DbScheduler stopping...");
        var started = TimeUtils.started();
        scheduler.stop();
        this.scheduler = null;
        logger.info("DbScheduler stopped in {}", TimeUtils.tookForLogging(started));
    }
}
