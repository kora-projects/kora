package io.koraframework.scheduling.jdk;

import io.koraframework.application.graph.ApplicationGraphDraw;
import io.koraframework.scheduling.common.telemetry.SchedulingObservation;
import io.koraframework.scheduling.common.telemetry.SchedulingTelemetry;
import io.koraframework.scheduling.jdk.job.FixedDelayJob;
import io.koraframework.scheduling.jdk.job.SchedulingJdkJobLocks;
import io.opentelemetry.api.trace.Span;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@Timeout(15)
class KoraJdkJobLifecycleTest {

    @Test
    void graphShutdownReachesExecutorTimeoutWhileJobIsBlocked() throws Exception {
        var telemetry = telemetry();
        var started = new CountDownLatch(1);
        var unblock = new CountDownLatch(1);
        var interrupted = new CountDownLatch(1);
        var draw = new ApplicationGraphDraw(KoraJdkJobLifecycleTest.class);
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

    @Test
    void jobReplacedOnConfigRefreshDoesNotOverlapWithRunningExecution() throws Exception {
        assertReplacedJobDoesNotOverlap(false);
    }

    @Test
    void jobReplacedOnExecutorRefreshDoesNotOverlapWithRunningExecution() throws Exception {
        assertReplacedJobDoesNotOverlap(true);
    }

    private static void assertReplacedJobDoesNotOverlap(boolean refreshExecutor) throws Exception {
        var running = new AtomicInteger();
        var maxRunning = new AtomicInteger();
        var started = new CountDownLatch(1);
        Runnable command = () -> {
            maxRunning.accumulateAndGet(running.incrementAndGet(), Math::max);
            started.countDown();
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                running.decrementAndGet();
            }
        };
        var delay = new AtomicReference<>(Duration.ofSeconds(10));
        var shutdownWait = new AtomicReference<>(Duration.ofSeconds(10));
        var draw = new ApplicationGraphDraw(KoraJdkJobLifecycleTest.class);
        var locksNode = draw.addNode(SchedulingJdkJobLocks.class, null, null,
            List.of(), List.of(), List.of(), g -> new SchedulingJdkJobLocks());
        var shutdownWaitNode = draw.addNode(Duration.class, null, null,
            List.of(), List.of(), List.of(), g -> shutdownWait.get());
        var executorNode = draw.addNode(VirtualThreadSchedulingJdkExecutor.class, null, null,
            List.of(shutdownWaitNode), List.of(shutdownWaitNode), List.of(), g -> {
                var wait = g.get(shutdownWaitNode);
                return new VirtualThreadSchedulingJdkExecutor(new SchedulingJdkConfig() {
                    @Override
                    public Duration shutdownWait() {
                        return wait;
                    }
                });
            });
        var delayNode = draw.addNode(Duration.class, null, null,
            List.of(), List.of(), List.of(), g -> delay.get());
        draw.addNode(FixedDelayJob.class, null, null,
            List.of(executorNode, delayNode, locksNode), List.of(executorNode, delayNode, locksNode), List.of(),
            g -> new FixedDelayJob(telemetry(), g.get(executorNode), command, Duration.ZERO, g.get(delayNode), true,
                g.get(locksNode).get(KoraJdkJobLifecycleTest.class, "job")));

        var graph = draw.init();
        try {
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            if (refreshExecutor) {
                shutdownWait.set(Duration.ofSeconds(20));
                graph.refresh(shutdownWaitNode);
            } else {
                delay.set(Duration.ofSeconds(20));
                graph.refresh(delayNode);
            }
            Thread.sleep(1500);
            assertThat(maxRunning.get()).as("executions of the same job running at once").isEqualTo(1);
        } finally {
            graph.release();
        }
    }

    @Test
    void sameJobInAnotherGraphIsNotBlockedByHungExecution() throws Exception {
        var hungStarted = new CountDownLatch(1);
        var unblock = new CountDownLatch(1);
        var otherRan = new CountDownLatch(1);
        var hungGraph = jobGraph(() -> {
            hungStarted.countDown();
            try {
                unblock.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).init();
        try {
            assertThat(hungStarted.await(5, TimeUnit.SECONDS)).isTrue();
            var otherGraph = jobGraph(otherRan::countDown).init();
            try {
                assertThat(otherRan.await(5, TimeUnit.SECONDS)).as("same job ran in the other graph").isTrue();
            } finally {
                otherGraph.release();
            }
        } finally {
            unblock.countDown();
            hungGraph.release();
        }
    }

    private static ApplicationGraphDraw jobGraph(Runnable command) {
        var draw = new ApplicationGraphDraw(KoraJdkJobLifecycleTest.class);
        var locksNode = draw.addNode(SchedulingJdkJobLocks.class, null, null,
            List.of(), List.of(), List.of(), g -> new SchedulingJdkJobLocks());
        var executorNode = draw.addNode(VirtualThreadSchedulingJdkExecutor.class, null, null,
            List.of(), List.of(), List.of(), g -> new VirtualThreadSchedulingJdkExecutor(new SchedulingJdkConfig() {}));
        draw.addNode(FixedDelayJob.class, null, null,
            List.of(executorNode, locksNode), List.of(executorNode, locksNode), List.of(),
            g -> new FixedDelayJob(telemetry(), g.get(executorNode), command, Duration.ZERO, Duration.ofHours(1), true,
                g.get(locksNode).get(KoraJdkJobLifecycleTest.class, "job")));
        return draw;
    }

    private static SchedulingTelemetry telemetry() {
        var telemetry = mock(SchedulingTelemetry.class);
        var observation = mock(SchedulingObservation.class);
        doReturn(KoraJdkJobLifecycleTest.class).when(telemetry).jobClass();
        when(telemetry.jobMethod()).thenReturn("job");
        when(telemetry.observe()).thenReturn(observation);
        when(observation.span()).thenReturn(Span.getInvalid());
        return telemetry;
    }
}
