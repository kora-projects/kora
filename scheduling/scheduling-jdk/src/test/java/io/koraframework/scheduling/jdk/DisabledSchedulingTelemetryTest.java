package io.koraframework.scheduling.jdk;

import io.koraframework.scheduling.common.telemetry.SchedulingTelemetryConfig;
import io.koraframework.scheduling.common.telemetry.impl.DefaultSchedulingTelemetryFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;

class DisabledSchedulingTelemetryTest {

    static final class CurrencyJob {}

    @Test
    void telemetryWithEverythingDisabledKeepsJobIdentity() {
        var config = mock(SchedulingTelemetryConfig.class, RETURNS_DEEP_STUBS);
        var factory = new DefaultSchedulingTelemetryFactory(config, null, null, null, null);

        var telemetry = factory.get("jdk", "job.currency", null, CurrencyJob.class, "refresh");

        assertThat(telemetry.jobClass()).isEqualTo(CurrencyJob.class);
        assertThat(telemetry.jobMethod()).isEqualTo("refresh");
    }
}
