package ru.tinkoff.kora.scheduling.quartz;

import org.quartz.Job;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.simpl.PropertySettingJobFactory;
import org.quartz.spi.JobFactory;
import org.quartz.spi.TriggerFiredBundle;
import ru.tinkoff.kora.application.graph.ValueOf;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KoraQuartzJobFactory implements JobFactory {
    private final Map<Class<? extends KoraQuartzJob>, ValueOf<KoraQuartzJob>> jobMap;
    private final JobFactory delegate = new PropertySettingJobFactory();

    public KoraQuartzJobFactory(List<ValueOf<KoraQuartzJob>> jobs) {
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
        return this.delegate.newJob(bundle, scheduler);
    }
}
