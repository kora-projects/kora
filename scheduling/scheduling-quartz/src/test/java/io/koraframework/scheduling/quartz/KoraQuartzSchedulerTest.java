package io.koraframework.scheduling.quartz;

import io.koraframework.application.graph.ValueOf;
import io.koraframework.scheduling.common.telemetry.SchedulingTelemetry;
import io.koraframework.scheduling.common.telemetry.impl.NoopSchedulingObservation;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.quartz.JobExecutionContext;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;

import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

class KoraQuartzSchedulerTest {

    @Test
    void shutdownInterruptsJobRunningLongerThanShutdownWait() throws Exception {
        var started = new CountDownLatch(1);
        var interrupted = new AtomicBoolean();
        var job = new FirstJob(context -> {
            started.countDown();
            try {
                Thread.sleep(Duration.ofMinutes(1));
            } catch (InterruptedException e) {
                interrupted.set(true);
            }
        }, List.of(trigger()));
        var scheduler = start(Duration.ofMillis(200), job);
        assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();

        var releaseStarted = System.nanoTime();
        scheduler.release();

        assertThat(interrupted).isTrue();
        assertThat(Duration.ofNanos(System.nanoTime() - releaseStarted)).isLessThan(Duration.ofSeconds(5));
    }

    @Test
    void shutdownWaitsForJobCompletingWithinShutdownWait() throws Exception {
        var started = new CountDownLatch(1);
        var completed = new AtomicBoolean();
        var interrupted = new AtomicBoolean();
        var job = new FirstJob(context -> {
            started.countDown();
            try {
                Thread.sleep(Duration.ofMillis(500));
                completed.set(true);
            } catch (InterruptedException e) {
                interrupted.set(true);
            }
        }, List.of(trigger()));
        var scheduler = start(Duration.ofSeconds(30), job);
        assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();

        scheduler.release();

        assertThat(completed).isTrue();
        assertThat(interrupted).isFalse();
    }

    @Test
    void duplicateTriggerIdentityIsRejected() throws Exception {
        var trigger = trigger();
        var first = new FirstJob(context -> {}, List.of(trigger));
        var second = new SecondJob(context -> {}, List.of(trigger.getTriggerBuilder().build()));
        var scheduler = new KoraQuartzScheduler(new KoraQuartzJobFactory(List.of(valueOf(first), valueOf(second))), properties(), new QuartzConfig() {});
        scheduler.init();
        try {
            var registrar = new KoraQuartzJobRegistrar(List.of(valueOf(first), valueOf(second)), scheduler.value(), new QuartzConfig() {});

            assertThatThrownBy(registrar::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Quartz trigger '" + trigger.getKey() + "' is declared more than once")
                .hasMessageContaining(FirstJob.class.getCanonicalName())
                .hasMessageContaining(SecondJob.class.getCanonicalName())
                .hasMessageContaining("@ScheduleQuartzWithCron(identity = ...)");
        } finally {
            scheduler.release();
        }
    }

    private static KoraQuartzScheduler start(Duration shutdownWait, KoraQuartzJob job) throws Exception {
        var config = new QuartzConfig() {
            @Override
            public Duration shutdownWait() {
                return shutdownWait;
            }
        };
        var scheduler = new KoraQuartzScheduler(new KoraQuartzJobFactory(List.of(valueOf(job))), properties(), config);
        scheduler.init();
        new KoraQuartzJobRegistrar(List.of(valueOf(job)), scheduler.value(), config).init();
        return scheduler;
    }

    private static Properties properties() {
        var properties = new Properties();
        properties.setProperty("org.quartz.scheduler.instanceName", UUID.randomUUID().toString());
        properties.setProperty("org.quartz.threadPool.threadCount", "2");
        return properties;
    }

    private static Trigger trigger() {
        return TriggerBuilder.newTrigger()
            .withIdentity(UUID.randomUUID().toString())
            .withSchedule(SimpleScheduleBuilder.repeatHourlyForever())
            .startNow()
            .build();
    }

    private static SchedulingTelemetry telemetry() {
        var telemetry = Mockito.mock(SchedulingTelemetry.class);
        when(telemetry.observe()).thenReturn(NoopSchedulingObservation.INSTANCE);
        return telemetry;
    }

    private static ValueOf<KoraQuartzJob> valueOf(KoraQuartzJob job) {
        return () -> job;
    }

    public static final class FirstJob extends KoraQuartzJob {
        public FirstJob(Consumer<JobExecutionContext> job, List<Trigger> triggers) {
            super(telemetry(), job, triggers);
        }
    }

    public static final class SecondJob extends KoraQuartzJob {
        public SecondJob(Consumer<JobExecutionContext> job, List<Trigger> triggers) {
            super(telemetry(), job, triggers);
        }
    }
}
