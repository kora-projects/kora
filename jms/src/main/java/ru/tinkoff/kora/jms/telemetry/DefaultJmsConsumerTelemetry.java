package ru.tinkoff.kora.jms.telemetry;

import javax.annotation.Nullable;
import javax.jms.JMSException;
import javax.jms.Message;

public final class DefaultJmsConsumerTelemetry implements JmsConsumerTelemetry {
    private static final JmsConsumerTelemetryContext NOOP_CTX = e -> {

    };
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
        var tracing = this.tracing;
        var metrics = this.metrics;
        var logger = this.logger;
        if (tracing == null && metrics == null && (logger == null || !logger.isEnabled())) {
            return DefaultJmsConsumerTelemetry.NOOP_CTX;
        }

        var start = System.nanoTime();
        var span = tracing == null ? null : tracing.get(message);
        if (logger != null) logger.onMessageReceived(message);
        return e -> {
            var duration = System.nanoTime() - start;
            if (logger != null) logger.onMessageProcessed(message, duration);
            if (metrics != null) metrics.onMessageProcessed(message, duration);
            if (span != null) span.close(e);
        };
    }
}
