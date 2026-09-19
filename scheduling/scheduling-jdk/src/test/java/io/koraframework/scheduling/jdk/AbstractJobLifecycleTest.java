package io.koraframework.scheduling.jdk;

import io.koraframework.application.graph.ApplicationGraphDraw;
import io.koraframework.scheduling.common.telemetry.SchedulingObservation;
import io.koraframework.scheduling.common.telemetry.SchedulingTelemetry;
import io.opentelemetry.api.trace.Span;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@Timeout(15)
class AbstractJobLifecycleTest {

    @Test
    void graphShutdownReachesExecutorTimeoutWhileJobIsBlocked() throws Exception {
        var telemetry = telemetry();
        var started = new CountDownLatch(1);
        var unblock = new CountDownLatch(1);
        var interrupted = new CountDownLatch(1);
        var draw = new ApplicationGraphDraw(AbstractJobLifecycleTest.class);
        var executorNode = draw.addNode(VirtualThreadSchedulingJdkExecutor.class, null, null,
            List.of(), List.of(), List.of(), g -> new VirtualThreadSchedulingJdkExecutor(new SchedulingJdkConfig() {
                @Override
                public Duration shutdownWait() {
                    return Duration.ofMillis(100);
                }
            }));
        draw.addNode(FixedDelayJob.class, null, null,
            List.of(executorNode), List.of(executorNode), List.of(), g -> new FixedDelayJob(telemetry, g.get(executorNode), () -> {
                started.countDown();
                try {
                    unblock.await();
                } catch (InterruptedException e) {
                    interrupted.countDown();
                    Thread.currentThread().interrupt();
                }
            }, Duration.ZERO, Duration.ofSeconds(1)));

        var graph = draw.init();
        var release = new FutureTask<Void>(() -> {
            graph.release();
            return null;
        });
        var releaser = Thread.ofVirtual().unstarted(release);
        try {
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            releaser.start();
            release.get(5, TimeUnit.SECONDS);
            assertThat(interrupted.await(5, TimeUnit.SECONDS)).isTrue();
        } finally {
            unblock.countDown();
            if (releaser.getState() == Thread.State.NEW) {
                graph.release();
            } else {
                release.get(5, TimeUnit.SECONDS);
            }
        }
    }

    @Test
    void releasingJobAllowsRunningCommandToFinishGracefully() throws Exception {
        var executor = new VirtualThreadSchedulingJdkExecutor(new SchedulingJdkConfig() {
            @Override
            public Duration shutdownWait() {
                return Duration.ofSeconds(2);
            }
        });
        var started = new CountDownLatch(1);
        var unblock = new CountDownLatch(1);
        var interrupted = new CountDownLatch(1);
        var completed = new CountDownLatch(1);
        var job = new FixedDelayJob(telemetry(), executor, () -> {
            started.countDown();
            try {
                unblock.await();
            } catch (InterruptedException e) {
                interrupted.countDown();
                Thread.currentThread().interrupt();
            } finally {
                completed.countDown();
            }
        }, Duration.ZERO, Duration.ofSeconds(1));
        executor.init();
        var release = new FutureTask<Void>(() -> {
            job.release();
            return null;
        });
        try {
            job.init();
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            Thread.ofVirtual().start(release);
            release.get(5, TimeUnit.SECONDS);
            assertThat(completed.getCount()).isEqualTo(1);
            unblock.countDown();
            executor.release();
            assertThat(completed.getCount()).isZero();
            assertThat(interrupted.getCount()).isEqualTo(1);
        } finally {
            unblock.countDown();
            job.release();
            executor.release();
        }
    }

    private static SchedulingTelemetry telemetry() {
        var telemetry = mock(SchedulingTelemetry.class);
        var observation = mock(SchedulingObservation.class);
        doReturn(AbstractJobLifecycleTest.class).when(telemetry).jobClass();
        when(telemetry.jobMethod()).thenReturn("job");
        when(telemetry.observe()).thenReturn(observation);
        when(observation.span()).thenReturn(Span.getInvalid());
        return telemetry;
    }
}
