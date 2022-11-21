package ru.tinkoff.kora.jms;

import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.jms.telemetry.DefaultJmsConsumerTelemetryFactory;
import ru.tinkoff.kora.jms.telemetry.JmsConsumerLoggerFactory;
import ru.tinkoff.kora.jms.telemetry.JmsConsumerMetricsFactory;
import ru.tinkoff.kora.jms.telemetry.JmsConsumerTracer;

import javax.annotation.Nullable;

public interface JmsConsumerModule {
    @DefaultComponent
    default DefaultJmsConsumerTelemetryFactory defaultJmsConsumerTelemetryFactory(
        @Nullable JmsConsumerLoggerFactory loggerFactory,
        @Nullable JmsConsumerMetricsFactory metricsFactory,
        @Nullable JmsConsumerTracer tracing) {
        return new DefaultJmsConsumerTelemetryFactory(loggerFactory, metricsFactory, tracing);
    }
}
