package io.koraframework.scheduling.quartz;

import org.quartz.InterruptableJob;
import org.quartz.JobExecutionContext;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.UnableToInterruptJobException;
import org.quartz.impl.SchedulerRepository;
import org.quartz.impl.StdSchedulerFactory;
import org.quartz.impl.matchers.GroupMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.koraframework.application.graph.Lifecycle;
import io.koraframework.application.graph.Wrapped;
import io.koraframework.common.util.TimeUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class KoraQuartzScheduler implements Wrapped<Scheduler>, Lifecycle {

    private static final Logger logger = LoggerFactory.getLogger(KoraQuartzScheduler.class);

    // Kora 1.x registered generated jobs, named like 'com.example.$Type_method_Job', in the DEFAULT group
    private static final Pattern LEGACY_GENERATED_JOB_NAME = Pattern.compile("(?:.*\\.)?\\$[^.]*_Job");
    private static final long SHUTDOWN_POLL_MILLIS = 50;

    private final KoraQuartzJobFactory jobFactory;
    private final Properties properties;
    private final QuartzConfig config;

    private volatile Scheduler scheduler = null;

    public KoraQuartzScheduler(KoraQuartzJobFactory jobFactory,
                               Properties properties,
                               QuartzConfig config) {
        this.jobFactory = jobFactory;
        this.properties = properties;
        this.config = config;
    }

    @Override
    public void init() throws SchedulerException {
        logger.debug("KoraQuartzScheduler starting...");
        var started = System.nanoTime();

        var propertiesToUse = new Properties();
        for (var property : this.properties.stringPropertyNames()) {
            propertiesToUse.setProperty(property, this.properties.getProperty(property));
        }

        // A graph refresh initializes this component before it releases the previous one. Quartz would hand out the
        // still running scheduler of the previous component, which its release then shuts down, so it is stopped first.
        var name = propertiesToUse.getProperty(StdSchedulerFactory.PROP_SCHED_INSTANCE_NAME, "QuartzScheduler");
        var previous = SchedulerRepository.getInstance().lookup(name);
        if (previous != null) {
            shutdown(previous);
        }

        var factory = new StdSchedulerFactory();
        factory.initialize(propertiesToUse);
        this.scheduler = factory.getScheduler();
        this.scheduler.setJobFactory(this.jobFactory);
        if (this.config.cleanupOrphanedJobs()) {
            // before start, so that misfire recovery never loads jobs of removed classes
            cleanupOrphanedJobs(this.scheduler, this.jobFactory.jobClasses());
        }
        // the scheduler is started by KoraQuartzJobRegistrar once the jobs are registered,
        // so that persisted triggers of disabled or changed jobs never fire
        this.scheduler.checkExists(JobKey.jobKey("_that_job_should_not_exist"));

        logger.info("KoraQuartzScheduler started in {}", TimeUtils.tookForLogging(started));
    }

    /**
     * Removes Kora jobs that are not registered in the application graph, see {@link QuartzConfig#cleanupOrphanedJobs()}.
     */
    static void cleanupOrphanedJobs(Scheduler scheduler, Set<Class<?>> knownJobClasses) throws SchedulerException {
        var knownNames = knownJobClasses.stream()
            .map(KoraQuartzJobRegistrar::jobName)
            .collect(Collectors.toSet());

        var removed = new ArrayList<JobKey>();
        for (var jobKey : scheduler.getJobKeys(GroupMatcher.jobGroupEquals(KoraQuartzJobRegistrar.JOB_GROUP))) {
            if (!knownNames.contains(jobKey.getName()) && scheduler.deleteJob(jobKey)) {
                removed.add(jobKey);
            }
        }
        for (var jobKey : scheduler.getJobKeys(GroupMatcher.jobGroupEquals(JobKey.DEFAULT_GROUP))) {
            // jobs of known classes are moved to the Kora group by KoraQuartzJobRegistrar
            var legacy = LEGACY_GENERATED_JOB_NAME.matcher(jobKey.getName()).matches();
            if (legacy && !knownNames.contains(jobKey.getName()) && scheduler.deleteJob(jobKey)) {
                removed.add(jobKey);
            }
        }

        if (!removed.isEmpty()) {
            logger.info("KoraQuartzScheduler removed Quartz jobs {} that are no longer present in the application graph", removed);
        }
    }

    @Override
    public void release() {
        var scheduler = this.scheduler;
        if (scheduler == null) {
            return;
        }
        shutdown(scheduler);
        this.scheduler = null;
    }

    private void shutdown(Scheduler scheduler) {
        var started = System.nanoTime();
        try {
            if (scheduler.isShutdown()) {
                // already shut down by the component that replaced this one on a graph refresh
                return;
            }
            logger.debug("KoraQuartzScheduler stopping...");
            scheduler.standby();
            var wait = this.config.shutdownWait();
            var running = awaitRunningJobs(scheduler, wait);
            if (!running.isEmpty()) {
                logger.warn("KoraQuartzScheduler interrupting jobs {} still running after {}", jobKeys(running), wait);
                for (var context : running) {
                    interrupt(context);
                }
                running = awaitRunningJobs(scheduler, wait);
                if (!running.isEmpty()) {
                    logger.warn("KoraQuartzScheduler stopped while jobs {} are still running after interruption", jobKeys(running));
                }
            }
            scheduler.shutdown(false);
        } catch (SchedulerException e) {
            logger.warn("KoraQuartzScheduler failed completing graceful shutdown", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("KoraQuartzScheduler interrupted while awaiting running jobs");
            try {
                scheduler.shutdown(false);
            } catch (SchedulerException ex) {
                logger.warn("KoraQuartzScheduler failed completing shutdown", ex);
            }
        }

        logger.info("KoraQuartzScheduler stopped in {}", TimeUtils.tookForLogging(started));
    }

    private static List<JobExecutionContext> awaitRunningJobs(Scheduler scheduler, Duration wait) throws SchedulerException, InterruptedException {
        var deadline = System.nanoTime() + saturatedNanos(wait);
        var running = scheduler.getCurrentlyExecutingJobs();
        while (!running.isEmpty() && deadline - System.nanoTime() > 0) {
            Thread.sleep(Math.min(SHUTDOWN_POLL_MILLIS, Math.max(1, (deadline - System.nanoTime()) / 1_000_000)));
            running = scheduler.getCurrentlyExecutingJobs();
        }
        return running;
    }

    private static long saturatedNanos(Duration duration) {
        try {
            return duration.toNanos();
        } catch (ArithmeticException e) {
            return Long.MAX_VALUE / 2;
        }
    }

    private static void interrupt(JobExecutionContext context) {
        if (context.getJobInstance() instanceof InterruptableJob job) {
            try {
                job.interrupt();
            } catch (UnableToInterruptJobException e) {
                logger.warn("KoraQuartzScheduler failed to interrupt job {}", context.getJobDetail().getKey(), e);
            }
        }
    }

    private static List<JobKey> jobKeys(List<JobExecutionContext> contexts) {
        return contexts.stream().map(c -> c.getJobDetail().getKey()).toList();
    }

    @Override
    public Scheduler value() {
        return this.scheduler;
    }
}
