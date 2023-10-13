package ru.tinkoff.kora.jms.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

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
    public JmsConsumerTelemetry get(TelemetryConfig config, String queueName) {
        var logger = this.loggerFactory == null ? null : this.loggerFactory.get(config.logging(), queueName);
        var metrics = this.metricsFactory == null ? null : this.metricsFactory.get(config.metrics(), queueName);
        return new DefaultJmsConsumerTelemetry(
            this.tracing, metrics, logger
        );
    }
}
