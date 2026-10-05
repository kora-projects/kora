package io.koraframework.http.server.common.system;

import io.koraframework.application.graph.All;
import io.koraframework.application.graph.PromiseOf;
import io.koraframework.common.liveness.LivenessProbe;
import io.koraframework.common.readiness.ReadinessProbe;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class ProbeHandlerTest {

    @Test
    void readinessRespondsWithTimeoutWhenProbeHangs() throws Exception {
        var interrupted = new CountDownLatch(1);
        ReadinessProbe hanging = () -> {
            hang(interrupted);
            return null;
        };
        PromiseOf<ReadinessProbe> probe = () -> Optional.of(hanging);
        var handler = new ReadinessHandler($SystemHttpServerConfig_ConfigValueMapper.DEFAULTS, All.of(probe));

        var response = assertTimeoutPreemptively(Duration.ofSeconds(35), () -> handler.handle(null));

        assertThat(response.code()).isEqualTo(408);
        assertThat(interrupted.await(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void livenessRespondsNotReadyWithoutWaitingForHangingProbe() throws Exception {
        var interrupted = new CountDownLatch(1);
        LivenessProbe hanging = () -> {
            hang(interrupted);
            return null;
        };
        PromiseOf<LivenessProbe> ready = () -> Optional.of(hanging);
        PromiseOf<LivenessProbe> notReady = Optional::empty;
        var handler = new LivenessHandler($SystemHttpServerConfig_ConfigValueMapper.DEFAULTS, All.of(ready, notReady));

        var response = assertTimeoutPreemptively(Duration.ofSeconds(5), () -> handler.handle(null));

        assertThat(response.code()).isEqualTo(503);
        assertThat(interrupted.await(5, TimeUnit.SECONDS)).isTrue();
    }

    private static void hang(CountDownLatch interrupted) {
        try {
            new CountDownLatch(1).await();
        } catch (InterruptedException e) {
            interrupted.countDown();
        }
    }
}
