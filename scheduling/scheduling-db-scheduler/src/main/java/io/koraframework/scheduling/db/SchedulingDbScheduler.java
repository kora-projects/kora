package io.koraframework.scheduling.db;

import com.github.kagkarlsson.scheduler.PollingStrategyConfig;
import com.github.kagkarlsson.scheduler.Scheduler;
import com.github.kagkarlsson.scheduler.SchedulerBuilder;
import com.github.kagkarlsson.scheduler.serializer.Serializer;
import com.github.kagkarlsson.scheduler.task.OnStartup;
import com.github.kagkarlsson.scheduler.task.Task;
import io.koraframework.application.graph.All;
import io.koraframework.application.graph.Lifecycle;
import io.koraframework.application.graph.ValueOf;
import io.koraframework.application.graph.Wrapped;
import io.koraframework.common.Configurer;
import io.koraframework.common.executor.BoundedVirtualThreadPerTaskExecutor;
import io.koraframework.common.util.TimeUtils;
import io.koraframework.scheduling.db.job.SchedulingDbJob;
import io.koraframework.scheduling.db.util.SchedulingDbInitializerUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;

public final class SchedulingDbScheduler implements Lifecycle, Wrapped<Scheduler> {

    private static final Logger logger = LoggerFactory.getLogger(SchedulingDbScheduler.class);

    private final DataSource dataSource;
    private final SchedulingDbConfig config;
    private final All<ValueOf<SchedulingDbJob>> jobs;
    @Nullable
    private final Configurer<SchedulerBuilder> schedulerBuilderConfigurer;

    private volatile Scheduler scheduler;

    public SchedulingDbScheduler(DataSource dataSource,
                                 SchedulingDbConfig config,
                                 All<ValueOf<SchedulingDbJob>> jobs,
                                 @Nullable Configurer<SchedulerBuilder> schedulerBuilderConfigurer) {
        this.dataSource = dataSource;
        this.config = config;
        this.jobs = jobs;
        this.schedulerBuilderConfigurer = schedulerBuilderConfigurer;
    }

    @Override
    public Scheduler value() {
        return scheduler;
    }

    @Override
    public void init() throws Exception {
        logger.debug("SchedulingDbScheduler starting...");
        var started = TimeUtils.started();

        if (this.config.initializeTable()) {
            SchedulingDbInitializerUtils.initialize(this.dataSource, this.config.tableName());
        }

        var tasks = new ArrayList<Task<?>>();
        var startupTasks = new ArrayList<Task<?>>();
        for (var value : this.jobs) {
            var job = value.get();
            var task = job.task();
            if (task instanceof OnStartup) {
                startupTasks.add(task);
            } else {
                tasks.add(task);
            }
        }

        var polling = this.config.polling();
        var prefetchMode = polling.prefetchMode();
        var schedulerBuilder = Scheduler.create(this.dataSource, tasks)
            .threads(this.config.executionParallelism())
            .executorService(new BoundedVirtualThreadPerTaskExecutor(this.config.executionParallelism(), Thread.ofVirtual().name("kora-scheduling-db-", 0)))
            .pollingInterval(polling.interval())
            .shutdownMaxWait(this.config.shutdownWait())
            .serializer(new Serializer() {
                @Override
                public byte[] serialize(Object data) {
                    return new byte[0];
                }

                @Override
                public <T> T deserialize(Class<T> clazz, byte[] serializedData) {
                    return null;
                }
            })
            .tableName(this.config.tableName());

        schedulerBuilder = startTasks(schedulerBuilder, startupTasks);

        schedulerBuilder = switch (polling.strategy()) {
            case FETCH -> schedulerBuilder.pollUsingFetch(
                prefetchMode == SchedulingDbConfig.PrefetchMode.DEFAULT
                    ? PollingStrategyConfig.DEFAULT_FETCH.lowerLimitFractionOfThreads
                    : prefetchMode.lowerLimitRatio(),
                prefetchMode == SchedulingDbConfig.PrefetchMode.DEFAULT
                    ? PollingStrategyConfig.DEFAULT_FETCH.upperLimitFractionOfThreads
                    : prefetchMode.upperLimitRatio()
            );
            case LOCK_AND_FETCH -> schedulerBuilder.pollUsingLockAndFetch(
                prefetchMode == SchedulingDbConfig.PrefetchMode.DEFAULT
                    ? PollingStrategyConfig.DEFAULT_SELECT_FOR_UPDATE.lowerLimitFractionOfThreads
                    : prefetchMode.lowerLimitRatio(),
                prefetchMode == SchedulingDbConfig.PrefetchMode.DEFAULT
                    ? PollingStrategyConfig.DEFAULT_SELECT_FOR_UPDATE.upperLimitFractionOfThreads
                    : prefetchMode.upperLimitRatio()
            );
        };

        if (schedulerBuilderConfigurer != null) {
            schedulerBuilder = schedulerBuilderConfigurer.configure(schedulerBuilder);
        }

        var scheduler = schedulerBuilder.build();

        scheduler.start();
        this.scheduler = scheduler;

        logger.info("SchedulingDbScheduler started in {}", TimeUtils.tookForLogging(started));
    }

    @Override
    public void release() {
        var scheduler = this.scheduler;
        if (scheduler == null) {
            return;
        }

        logger.debug("SchedulingDbScheduler stopping...");
        var started = TimeUtils.started();
        scheduler.stop();
        this.scheduler = null;
        logger.info("SchedulingDbScheduler stopped in {}", TimeUtils.tookForLogging(started));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static SchedulerBuilder startTasks(SchedulerBuilder schedulerBuilder, List<Task<?>> startupTasks) {
        return schedulerBuilder.startTasks((List) startupTasks);
    }
}
