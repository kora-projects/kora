package io.koraframework.nats.common.producer;

import io.koraframework.config.common.annotation.ConfigMapper;
import io.koraframework.nats.common.NatsConnectionConfig;
import io.koraframework.nats.common.producer.telemetry.NatsPublisherTelemetryConfig;

import java.time.Duration;

@ConfigMapper
public interface NatsPublisherConfig extends NatsConnectionConfig {

    default Mode mode() {
        return Mode.CORE;
    }

    default Duration requestTimeout() {
        return Duration.ofSeconds(5);
    }

    NatsPublisherTelemetryConfig telemetry();

    enum Mode {
        CORE, JETSTREAM
    }

    @ConfigMapper
    interface SubjectConfig {

        String subject();
    }
}
