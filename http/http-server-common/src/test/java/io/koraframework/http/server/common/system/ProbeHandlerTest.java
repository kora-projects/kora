package io.koraframework.http.server.common.system;

import io.koraframework.application.graph.All;
import io.koraframework.application.graph.PromiseOf;
import io.koraframework.common.liveness.LivenessProbe;
import io.koraframework.common.liveness.LivenessProbeFailure;
import io.koraframework.common.readiness.ReadinessProbe;
import io.koraframework.common.readiness.ReadinessProbeFailure;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ProbeHandlerTest {

    @Test
    void readinessFailureWithNullMessageRespondsNotReady() throws Exception {
        ReadinessProbe failing = () -> new ReadinessProbeFailure(null);
        PromiseOf<ReadinessProbe> probe = () -> Optional.of(failing);
        var handler = new ReadinessHandler($SystemHttpServerConfig_ConfigValueMapper.DEFAULTS, All.of(probe));

        assertThat(handler.handle(null).code()).isEqualTo(503);
    }

    @Test
    void livenessFailureWithNullMessageRespondsNotReady() throws Exception {
        LivenessProbe failing = () -> new LivenessProbeFailure(null);
        PromiseOf<LivenessProbe> probe = () -> Optional.of(failing);
        var handler = new LivenessHandler($SystemHttpServerConfig_ConfigValueMapper.DEFAULTS, All.of(probe));

        assertThat(handler.handle(null).code()).isEqualTo(503);
    }
}
