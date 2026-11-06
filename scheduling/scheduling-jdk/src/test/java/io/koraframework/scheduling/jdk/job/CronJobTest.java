package io.koraframework.scheduling.jdk.job;

import io.koraframework.common.telemetry.Observation;
import io.koraframework.scheduling.common.telemetry.SchedulingObservation;
import io.koraframework.scheduling.common.telemetry.SchedulingTelemetry;
import io.koraframework.scheduling.jdk.SchedulingJdkExecutor;
import io.koraframework.scheduling.jdk.util.CronExpression;
import io.opentelemetry.api.trace.Span;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CronJobTest {

    private final SchedulingTelemetry telemetry = mock(SchedulingTelemetry.class);
    private final SchedulingObservation observation = mock(SchedulingObservation.class);
    private final SchedulingJdkExecutor executor = mock(SchedulingJdkExecutor.class);
    private final List<Runnable> executions = new ArrayList<>();
    private final List<ScheduledFuture<?>> futures = new ArrayList<>();

    @BeforeEach
    void setup() {
        doReturn(CronJobTest.class).when(this.telemetry).jobClass();
        when(this.telemetry.jobMethod()).thenReturn("job");
        when(this.telemetry.observe()).thenReturn(this.observation);
        when(this.observation.span()).thenReturn(Span.getInvalid());
        doAnswer(invocation -> recordExecution(invocation.getArgument(0)))
            .when(this.executor).scheduleOnce(any(), anyLong(), any(TimeUnit.class));
    }

    @Test
    void reschedulesAfterCompletionAndKeepsTelemetryForUserFailures() {
        var failure = new IllegalStateException("job failed");
        var command = mock(Runnable.class);
        doAnswer(invocation -> {
            assertThat(Observation.current(SchedulingObservation.class)).isSameAs(this.observation);
            // No subsequent execution has been scheduled while the command is running.
            assertThat(this.executions).hasSize(mockingDetails(command).getInvocations().size());
            throw failure;
        }).when(command).run();
        var job = cronJob(command);

        job.init();
        this.executions.get(0).run();
        this.executions.get(1).run();

        assertThat(this.executions).hasSize(3);
        verify(command, times(2)).run();
        verify(this.observation, times(2)).observeRun();
        verify(this.observation, times(2)).observeError(failure);
        verify(this.observation, times(2)).end();
        job.release();
        verify(this.futures.get(2)).cancel(false);
    }

    @Test
    void initIsIdempotentAndReleaseCancelsLatestExecution() {
        var command = mock(Runnable.class);
        var job = cronJob(command);
        job.init();
        job.init();
        assertThat(this.executions).hasSize(1);
        this.executions.getFirst().run();
        assertThat(this.executions).hasSize(2);

        job.release();
        job.release();
        verify(this.futures.get(1), times(1)).cancel(false);
        verify(this.futures.get(0), never()).cancel(anyBoolean());
        this.executions.get(1).run();
        verify(command, times(1)).run();
        assertThat(this.executions).hasSize(2);
    }

    @Test
    void releaseFromCommandDoesNotScheduleAnotherExecution() {
        var reference = new AtomicReference<CronJob>();
        var job = cronJob(() -> reference.get().release());
        reference.set(job);
        job.init();
        this.executions.getFirst().run();
        assertThat(this.executions).hasSize(1);
        verify(this.futures.getFirst()).cancel(false);
    }

    @Test
    void cronWithoutNextFireTimeCanBeInitializedAndReleased() {
        var command = mock(Runnable.class);
        var job = new CronJob(this.telemetry, this.executor, command, CronExpression.parse("0 0 0 1 JAN ? 1970"));
        job.init();
        job.release();
        verifyNoInteractions(this.executor, command);
        verify(this.telemetry, never()).observe();
    }

    @Test
    void failedInitialSchedulingCanBeRetried() {
        doThrow(new RejectedExecutionException("not started"))
            .doAnswer(invocation -> recordExecution(invocation.getArgument(0)))
            .when(this.executor).scheduleOnce(any(), anyLong(), any(TimeUnit.class));
        var job = cronJob(() -> {});
        assertThatThrownBy(job::init).isInstanceOf(RejectedExecutionException.class);

        job.init();
        assertThat(this.executions).hasSize(1);
        job.release();
        verify(this.futures.getFirst()).cancel(false);
    }

    @Test
    void telemetryFailureDoesNotReschedule() {
        var failure = new IllegalStateException("telemetry failed");
        doThrow(failure).when(this.observation).end();
        var job = cronJob(() -> {});
        job.init();
        assertThatThrownBy(this.executions.getFirst()::run).isSameAs(failure);
        assertThat(this.executions).hasSize(1);
        job.release();
    }

    @Test
    void fixedDelayRepetitionRemainsManagedByExecutor() {
        doAnswer(invocation -> recordExecution(invocation.getArgument(0)))
            .when(this.executor).scheduleWithFixedDelay(any(), anyLong(), anyLong(), any());
        var job = new FixedDelayJob(this.telemetry, this.executor, () -> {}, Duration.ZERO, Duration.ofSeconds(1));
        job.init();
        this.executions.getFirst().run();
        this.executions.getFirst().run();
        assertThat(this.executions).hasSize(1);
        verify(this.executor, times(1)).scheduleWithFixedDelay(any(), eq(0L), eq(1000L), eq(TimeUnit.MILLISECONDS));
        verify(this.executor, never()).scheduleOnce(any(), anyLong(), any());
        job.release();
    }

    @Test
    void oneShotDoesNotReschedule() {
        var job = new RunOnceJob(this.telemetry, this.executor, () -> {}, Duration.ZERO);
        job.init();
        this.executions.getFirst().run();
        assertThat(this.executions).hasSize(1);
        job.release();
    }

    @Test
    void preservesSubMillisecondDelay() {
        var now = ZonedDateTime.parse("2026-09-16T12:00:00.999500+03:00[Europe/Moscow]");
        var clock = Clock.fixed(now.toInstant(), now.getZone());
        var job = new CronJob(this.telemetry, this.executor, () -> {}, CronExpression.parse("* * * * * *"), clock);
        job.init();
        verify(this.executor).scheduleOnce(any(), eq(500_000L), eq(TimeUnit.NANOSECONDS));
        job.release();
    }

    @Test
    void earlyTimerFireDoesNotRunTheSameCronSlotTwice() {
        var start = ZonedDateTime.parse("2026-09-16T12:00:00.500+03:00[Europe/Moscow]");
        var clock = new MutableClock(start);
        var job = new CronJob(this.telemetry, this.executor, () -> {}, CronExpression.parse("* * * * * *"), clock);
        job.init();
        verify(this.executor).scheduleOnce(any(), eq(500_000_000L), eq(TimeUnit.NANOSECONDS));

        // The timer fires 20ms before the planned 12:00:01, e.g. the wall clock is slewed by NTP.
        clock.now = start.plusNanos(480_000_000).toInstant();
        this.executions.getFirst().run();

        // The next execution is planned for 12:00:02, not for 12:00:01 again.
        verify(this.executor).scheduleOnce(any(), eq(1_020_000_000L), eq(TimeUnit.NANOSECONDS));
        job.release();
    }

    @Test
    void wallClockSteppedBackwardsKeepsCronPeriod() {
        var start = ZonedDateTime.parse("2026-10-06T10:00:30Z");
        var clock = new MutableClock(start);
        var job = new CronJob(this.telemetry, this.executor, () -> {}, CronExpression.parse("0 * * * * ?"), clock);
        job.init();
        verify(this.executor).scheduleOnce(any(), eq(30_000_000_000L), eq(TimeUnit.NANOSECONDS));

        // The 10:01:00 run starts, then the wall clock is stepped back by one hour (NTP step, VM restore).
        clock.now = Instant.parse("2026-10-06T10:01:00Z");
        var run = this.executions.getFirst();
        clock.now = Instant.parse("2026-10-06T09:01:00Z");
        run.run();

        // The next run is planned for 09:02:00, not for 10:02:00 an hour later.
        verify(this.executor).scheduleOnce(any(), eq(60_000_000_000L), eq(TimeUnit.NANOSECONDS));
        job.release();
    }

    @Test
    void restartBeforeCronSlotKeepsThatSlot() {
        var start = ZonedDateTime.parse("2026-09-16T12:00:00.500+03:00[Europe/Moscow]");
        var clock = new MutableClock(start);
        var job = new CronJob(this.telemetry, this.executor, () -> {}, CronExpression.parse("* * * * * *"), clock);
        job.init();
        job.release();

        clock.now = start.plusNanos(200_000_000).toInstant();
        job.init();

        // 12:00:01 was cancelled before it fired, so it is planned again
        verify(this.executor).scheduleOnce(any(), eq(300_000_000L), eq(TimeUnit.NANOSECONDS));
        job.release();
    }

    @Test
    void staleExecutionCannotRunAfterRestart() {
        var command = mock(Runnable.class);
        var job = cronJob(command);
        job.init();
        var stale = this.executions.getFirst();
        job.release();
        job.init();
        stale.run();
        verifyNoInteractions(command);
        assertThat(this.executions).hasSize(2);
        this.executions.get(1).run();
        verify(command).run();
        assertThat(this.executions).hasSize(3);
        job.release();
    }

    private CronJob cronJob(Runnable command) {
        return new CronJob(this.telemetry, this.executor, command, CronExpression.parse("* * * * * *"));
    }

    private static final class MutableClock extends Clock {
        private final ZoneId zone;
        private volatile Instant now;

        private MutableClock(ZonedDateTime now) {
            this.zone = now.getZone();
            this.now = now.toInstant();
        }

        @Override
        public ZoneId getZone() {
            return this.zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Instant instant() {
            return this.now;
        }
    }

    private ScheduledFuture<?> recordExecution(Runnable command) {
        var future = mock(ScheduledFuture.class);
        this.executions.add(command);
        this.futures.add(future);
        return future;
    }
}
