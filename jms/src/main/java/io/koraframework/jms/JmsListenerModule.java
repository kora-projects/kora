package io.koraframework.jms;

import io.koraframework.common.annotation.DefaultComponent;
import io.koraframework.jms.telemetry.JmsConsumerTelemetryFactory;
import io.koraframework.jms.telemetry.impl.DefaultJmsConsumerLoggerFactory;
import io.koraframework.jms.telemetry.impl.DefaultJmsConsumerMetricsFactory;
import io.koraframework.jms.telemetry.impl.DefaultJmsConsumerTelemetryFactory;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import org.jspecify.annotations.Nullable;

import javax.jms.ConnectionFactory;

public interface JmsListenerModule {

    @DefaultComponent
    default JmsMessageListenerContainerFactory defaultJmsMessageListenerContainerFactory(
        ConnectionFactory connectionFactory,
        JmsConsumerTelemetryFactory telemetry
    ) {
        return new JmsMessageListenerContainerFactory(connectionFactory, telemetry);
    }

    @DefaultComponent
    default JmsConsumerTelemetryFactory defaultJmsConsumerTelemetryFactory(
        @Nullable Tracer tracer,
        @Nullable MeterRegistry meterRegistry,
        @Nullable DefaultJmsConsumerLoggerFactory loggerFactory,
        @Nullable DefaultJmsConsumerMetricsFactory metricsFactory
    ) {
        return new DefaultJmsConsumerTelemetryFactory(tracer, meterRegistry, loggerFactory, metricsFactory);
    }
}
