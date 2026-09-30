package io.koraframework.scheduling.quartz;

import io.koraframework.application.graph.ValueOf;
import org.quartz.Job;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.simpl.PropertySettingJobFactory;
import org.quartz.spi.JobFactory;
import org.quartz.spi.TriggerFiredBundle;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class KoraQuartzJobFactory implements JobFactory {
    private final Map<Class<? extends KoraQuartzJob>, ValueOf<KoraQuartzJob>> jobMap;
    private final JobFactory delegate = new PropertySettingJobFactory();
    private final Iterable<ValueOf<KoraQuartzJob>> jobs;

    public KoraQuartzJobFactory(Iterable<ValueOf<KoraQuartzJob>> jobs) {
        this.jobs = jobs;
        this.jobMap = new HashMap<>();
        for (var job : jobs) {
            var realJob = job.get();
            if (this.jobMap.put(realJob.getClass(), job) != null) {
                throw new IllegalStateException("Duplicate Quartz job class registered: %s; make sure only one KoraQuartzJob component exists for this class".formatted(realJob.getClass().getCanonicalName()));
            }
        }
    }

    @Override
    public Job newJob(TriggerFiredBundle bundle, Scheduler scheduler) throws SchedulerException {
        var type = bundle.getJobDetail().getJobClass();
        var job = this.jobMap.get(type);
        if (job != null) {
            return job.get();
        }
        for (var j : this.jobs) {
            var realJob = j.get();
            if (realJob.getClass().equals(type)) {
                return realJob;
            }
        }
        return this.delegate.newJob(bundle, scheduler);
    }

    /**
     * @return classes of the Kora jobs present in the application graph
     */
    public Set<Class<?>> jobClasses() {
        return new HashSet<>(this.jobMap.keySet());
    }

    // todo we should cleanup job map of conditional ValueOf's on graph refresh
}
