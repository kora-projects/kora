package ru.tinkoff.kora.jms.telemetry;

import javax.annotation.Nullable;
import javax.jms.JMSException;
import javax.jms.Message;

public final class DefaultJmsConsumerTelemetry implements JmsConsumerTelemetry {
    @Nullable
    private final JmsConsumerTracer tracing;
    @Nullable
    private final JmsConsumerMetrics metrics;
    @Nullable
    private final JmsConsumerLogger logger;

    public DefaultJmsConsumerTelemetry(@Nullable JmsConsumerTracer tracing, @Nullable JmsConsumerMetrics metrics, @Nullable JmsConsumerLogger logger) {
        this.tracing = tracing;
        this.metrics = metrics;
        this.logger = logger;
    }

    @Override
    public JmsConsumerTelemetryContext get(Message message) throws JMSException {
        var start = System.nanoTime();
        var span = this.tracing == null ? null : this.tracing.get(message);
        if (this.logger != null) this.logger.onMessageReceived(message);
        return e -> {
            var duration = System.nanoTime() - start;
            if (this.logger != null) this.logger.onMessageProcessed(message, duration);
            if (this.metrics != null) this.metrics.onMessageProcessed(message, duration);
            if (span != null) span.close(e);
        };
    }
}
