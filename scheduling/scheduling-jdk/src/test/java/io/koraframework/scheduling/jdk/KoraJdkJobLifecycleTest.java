package io.koraframework.scheduling.jdk;

import io.koraframework.application.graph.ApplicationGraphDraw;
import io.koraframework.application.graph.Lifecycle;
import io.koraframework.scheduling.common.telemetry.SchedulingObservation;
import io.koraframework.scheduling.common.telemetry.SchedulingTelemetry;
import io.koraframework.scheduling.jdk.job.FixedDelayJob;
import io.opentelemetry.api.trace.Span;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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
    void graphShutdownOfBlockedJobTakesSingleShutdownWait() throws Exception {
        var started = new CountDownLatch(1);
        var interrupted = new CountDownLatch(1);
        var draw = new ApplicationGraphDraw(KoraJdkJobLifecycleTest.class);
        var executorNode = draw.addNode(VirtualThreadSchedulingJdkExecutor.class, null, null,
            List.of(), List.of(), List.of(), g -> new VirtualThreadSchedulingJdkExecutor(new SchedulingJdkConfig() {
                @Override
                public Duration shutdownWait() {
                    return Duration.ofSeconds(1);
                }
            }));
        draw.addNode(FixedDelayJob.class, null, null,
            List.of(executorNode), List.of(executorNode), List.of(), g -> new FixedDelayJob(telemetry(), g.get(executorNode), () -> {
                started.countDown();
                try {
                    Thread.sleep(Duration.ofHours(1));
                } catch (InterruptedException e) {
                    interrupted.countDown();
                    Thread.currentThread().interrupt();
                }
            }, Duration.ZERO, Duration.ofHours(1)));

        var graph = draw.init();
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
        var releaseStarted = System.nanoTime();
        graph.release();
        var took = Duration.ofNanos(System.nanoTime() - releaseStarted);
        assertThat(interrupted.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(took).as("graph shutdown time").isLessThan(Duration.ofMillis(1800));
    }

    @Test
    void graphShutdownKeepsJobDependenciesAliveUntilRunningCommandFinishes() throws Exception {
        var started = new CountDownLatch(1);
        var finished = new CountDownLatch(1);
        var dependencyReleasedWhileRunning = new AtomicBoolean();
        var draw = new ApplicationGraphDraw(KoraJdkJobLifecycleTest.class);
        var executorNode = draw.addNode(VirtualThreadSchedulingJdkExecutor.class, null, null,
            List.of(), List.of(), List.of(), g -> new VirtualThreadSchedulingJdkExecutor(new SchedulingJdkConfig() {
                @Override
                public Duration shutdownWait() {
                    return Duration.ofSeconds(10);
                }
            }));
        var dependencyNode = draw.addNode(JobDependency.class, null, null,
            List.of(), List.of(), List.of(), g -> new JobDependency());
        draw.addNode(FixedDelayJob.class, null, null,
            List.of(executorNode, dependencyNode), List.of(executorNode, dependencyNode), List.of(), g -> {
                var dependency = g.get(dependencyNode);
                return new FixedDelayJob(telemetry(), g.get(executorNode), () -> {
                    started.countDown();
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    dependencyReleasedWhileRunning.set(dependency.released.get());
                    finished.countDown();
                }, Duration.ZERO, Duration.ofHours(1));
            });

        var graph = draw.init();
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
        graph.release();
        assertThat(finished.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(dependencyReleasedWhileRunning.get())
            .as("job dependency released while the job was still running")
            .isFalse();
    }

    private static final class JobDependency implements Lifecycle {
        private final AtomicBoolean released = new AtomicBoolean();

        @Override
        public void init() {}

        @Override
        public void release() {
            this.released.set(true);
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
            Thread.sleep(200);
            assertThat(release.isDone()).as("job release waits for the running command").isFalse();
            assertThat(completed.getCount()).isEqualTo(1);
            unblock.countDown();
            release.get(5, TimeUnit.SECONDS);
            assertThat(completed.getCount()).isZero();
            assertThat(interrupted.getCount()).isEqualTo(1);
            executor.release();
        } finally {
            unblock.countDown();
            job.release();
            executor.release();
        }
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
