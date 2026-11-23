package io.koraframework.scheduling.jdk;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.koraframework.application.graph.ApplicationGraphDraw;
import io.koraframework.scheduling.common.telemetry.SchedulingObservation;
import io.koraframework.scheduling.common.telemetry.SchedulingTelemetry;
import io.koraframework.scheduling.common.telemetry.SchedulingTelemetryConfig;
import io.koraframework.scheduling.common.telemetry.impl.DefaultSchedulingTelemetryFactory;
import io.koraframework.scheduling.jdk.job.FixedDelayJob;
import io.opentelemetry.api.trace.Span;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

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

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void jobFailureIsLoggedOnceWhateverTelemetryLoggingIs(boolean loggingEnabled) throws Exception {
        var config = new SchedulingTelemetryConfig() {
            @Override
            public SchedulingLoggingConfig logging() {
                return new SchedulingLoggingConfig() {
                    @Override
                    public boolean enabled() {
                        return loggingEnabled;
                    }
                };
            }

            @Override
            public SchedulingMetricsConfig metrics() {
                return new SchedulingMetricsConfig() {};
            }

            @Override
            public SchedulingTracingConfig tracing() {
                return new SchedulingTracingConfig() {};
            }
        };
        var telemetry = new DefaultSchedulingTelemetryFactory(config, null, null, null, null)
            .get("jdk", null, null, KoraJdkJobLifecycleTest.class, "job");
        var executor = new VirtualThreadSchedulingJdkExecutor(new SchedulingJdkConfig() {});
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        var root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        root.addAppender(appender);
        var failed = new CountDownLatch(1);
        var job = new FixedDelayJob(telemetry, executor, () -> {
            failed.countDown();
            throw new IllegalStateException("boom from job");
        }, Duration.ZERO, Duration.ofHours(1));
        executor.init();
        try {
            job.init();
            assertThat(failed.await(5, TimeUnit.SECONDS)).isTrue();
        } finally {
            job.release();
            executor.release();
            root.detachAppender(appender);
        }
        assertThat(appender.list)
            .filteredOn(e -> e.getThrowableProxy() != null && "boom from job".equals(e.getThrowableProxy().getMessage()))
            .hasSize(1);
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
