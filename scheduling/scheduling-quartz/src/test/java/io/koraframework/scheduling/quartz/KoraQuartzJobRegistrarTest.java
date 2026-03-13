package io.koraframework.scheduling.quartz;

import io.koraframework.application.graph.ValueOf;
import io.koraframework.scheduling.common.telemetry.NoopSchedulingObservation;
import io.koraframework.scheduling.common.telemetry.SchedulingTelemetry;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.quartz.JobExecutionContext;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;

import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class KoraQuartzJobRegistrarTest {

    @Test
    void testRegistrarWorks() throws Exception {
        java.util.logging.Logger.getLogger("org.quartz").setLevel(java.util.logging.Level.OFF);
        var telemetry = Mockito.mock(SchedulingTelemetry.class);
        when(telemetry.observe()).thenReturn(NoopSchedulingObservation.INSTANCE);
        var trigger1 = TriggerBuilder.newTrigger()
            .withIdentity(UUID.randomUUID().toString())
            .withSchedule(SimpleScheduleBuilder.repeatSecondlyForever())
            .build();
        var trigger2 = TriggerBuilder.newTrigger()
            .withIdentity(UUID.randomUUID().toString())
            .withSchedule(SimpleScheduleBuilder.repeatSecondlyForever())
            .build();
        var trigger3 = TriggerBuilder.newTrigger()
            .withIdentity(UUID.randomUUID().toString())
            .withSchedule(SimpleScheduleBuilder.repeatSecondlyForever())
            .build();

        @SuppressWarnings("unchecked")
        var mockJobRunnable = (Consumer<JobExecutionContext>) Mockito.mock(Consumer.class);

        var testJob = new TestValueOf<KoraQuartzJob>();
        testJob.value = new TestJob(telemetry, mockJobRunnable, List.of(trigger1, trigger2, trigger3));

        var jobFactory = new KoraQuartzJobFactory(List.of(testJob));
        var properties = new Properties();
        properties.setProperty("org.quartz.threadPool.threadCount", "1");
        var scheduler = new KoraQuartzScheduler(jobFactory, properties, new SchedulingQuartzConfig() {});
        try {
            scheduler.init();

            var registrar = new KoraQuartzJobRegistrar(List.of(testJob), scheduler.value());

            try {
                registrar.init();
                Thread.sleep(5100);
                Mockito.verify(
                    mockJobRunnable,
                    (data) -> Assertions.assertThat(data.getAllInvocations())
                        .hasSizeGreaterThan(4 * 3)
                        .hasSizeLessThanOrEqualTo(6 * 3)
                ).accept(any());
                Mockito.reset(mockJobRunnable);
                var newTrigger = TriggerBuilder.newTrigger()
                    .withIdentity(UUID.randomUUID().toString())
                    .withSchedule(SimpleScheduleBuilder.repeatSecondlyForever())
                    .build();
                var changedTrigger = trigger2.getTriggerBuilder()
                    .withSchedule(SimpleScheduleBuilder.repeatSecondlyForever(2))
                    .build();
                testJob.value = new TestJob(telemetry, mockJobRunnable, List.of(trigger1, changedTrigger, newTrigger));

                registrar.graphRefreshed();
                Thread.sleep(6500);
                Mockito.verify(
                    mockJobRunnable,
                    (data) -> Assertions.assertThat(data.getAllInvocations())
                        .hasSizeGreaterThan(5 * 2 + 1)
                        .hasSizeLessThanOrEqualTo(7 * 2 + 4)
                ).accept(any());
            } finally {
                registrar.release();
            }
        } finally {
            scheduler.release();
        }
    }

    private static class TestJob extends KoraQuartzJob {

        public TestJob(SchedulingTelemetry telemetry, Consumer<JobExecutionContext> job, List<Trigger> trigger) {
            super(telemetry, job, trigger);
        }
    }

    private static class TestValueOf<T> implements ValueOf<T> {
        private volatile T value;

        @Override
        public T get() {
            return value;
        }
    }
}
