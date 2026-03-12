package io.koraframework.scheduling.quartz;

import io.koraframework.application.graph.ValueOf;
import org.quartz.Job;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.simpl.PropertySettingJobFactory;
import org.quartz.spi.JobFactory;
import org.quartz.spi.TriggerFiredBundle;

import java.util.HashMap;
import java.util.Map;

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
                throw new IllegalStateException("Duplicate key");
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

    // todo we should cleanup job map of conditional ValueOf's on graph refresh
}
