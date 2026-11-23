package io.koraframework.nats.common.producer;

import io.koraframework.application.graph.Lifecycle;
import io.koraframework.nats.common.NatsClient;
import io.koraframework.nats.common.producer.telemetry.NatsPublisherTelemetry;

public interface GeneratedNatsPublisher extends Lifecycle {

    NatsClient client();

    NatsPublisherTelemetry telemetry();
}
