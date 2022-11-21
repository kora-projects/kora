package ru.tinkoff.kora.jms.telemetry;

import javax.annotation.Nullable;

public final class DefaultJmsConsumerTelemetryFactory implements JmsConsumerTelemetryFactory {
    @Nullable
    private final JmsConsumerLoggerFactory loggerFactory;
    @Nullable
    private final JmsConsumerMetricsFactory metricsFactory;
    @Nullable
    private final JmsConsumerTracer tracing;

    public DefaultJmsConsumerTelemetryFactory(@Nullable JmsConsumerLoggerFactory loggerFactory, @Nullable JmsConsumerMetricsFactory metricsFactory, @Nullable JmsConsumerTracer tracing) {
        this.loggerFactory = loggerFactory;
        this.metricsFactory = metricsFactory;
        this.tracing = tracing;
    }

    @Override
    public JmsConsumerTelemetry get(String queueName) {
        var logger = this.loggerFactory == null ? null : this.loggerFactory.get(queueName);
        var metrics = this.metricsFactory == null ? null : this.metricsFactory.get(queueName);
        return new DefaultJmsConsumerTelemetry(
            this.tracing, metrics, logger
        );
    }
}
