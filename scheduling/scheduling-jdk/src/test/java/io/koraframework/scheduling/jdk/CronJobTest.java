package io.koraframework.scheduling.jdk;

import io.koraframework.common.telemetry.Observation;
import io.koraframework.scheduling.common.telemetry.SchedulingObservation;
import io.koraframework.scheduling.common.telemetry.SchedulingTelemetry;
import io.opentelemetry.api.trace.Span;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
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

    private ScheduledFuture<?> recordExecution(Runnable command) {
        var future = mock(ScheduledFuture.class);
        this.executions.add(command);
        this.futures.add(future);
        return future;
    }
}
