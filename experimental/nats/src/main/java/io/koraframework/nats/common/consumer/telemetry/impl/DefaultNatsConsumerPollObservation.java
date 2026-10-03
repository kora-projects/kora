package io.koraframework.nats.common.consumer.telemetry.impl;

import io.koraframework.nats.common.consumer.NatsMessages;
import io.koraframework.nats.common.consumer.telemetry.NatsConsumerPollObservation;
import io.koraframework.nats.common.consumer.telemetry.NatsConsumerRecordObservation;
import io.koraframework.nats.common.consumer.telemetry.impl.DefaultNatsConsumerLoggerFactory.DefaultNatsConsumerLogger;
import io.koraframework.nats.common.consumer.telemetry.impl.DefaultNatsConsumerMetricsFactory.DefaultNatsConsumerMetrics;
import io.koraframework.nats.common.consumer.telemetry.impl.DefaultNatsConsumerTelemetry.TelemetryContext;
import io.nats.client.Message;
import io.opentelemetry.api.trace.Span;

public class DefaultNatsConsumerPollObservation extends DefaultNatsConsumerOperationObservation implements NatsConsumerPollObservation {
    private final DefaultNatsConsumerTelemetry telemetry;

    public DefaultNatsConsumerPollObservation(TelemetryContext context, DefaultNatsConsumerLogger logger, DefaultNatsConsumerMetrics metrics,
                                              Span span, DefaultNatsConsumerTelemetry telemetry) {
        super(context, logger, metrics, span, "poll", "");
        this.telemetry = telemetry;
    }

    @Override
    public void observeRecords(NatsMessages<?> records) {
        span.setAttribute("messaging.batch.message_count", records.count());
        for (var record : records) {
            metrics.count("receive", record.subject(), 1);
        }
    }

    @Override
    public NatsConsumerRecordObservation observeRecord(Message message) {
        return telemetry.record(message);
    }
}
