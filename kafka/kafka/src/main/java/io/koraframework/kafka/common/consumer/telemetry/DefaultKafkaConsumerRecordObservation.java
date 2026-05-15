package io.koraframework.kafka.common.consumer.telemetry;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.semconv.ErrorAttributes;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.jspecify.annotations.Nullable;

public class DefaultKafkaConsumerRecordObservation implements KafkaConsumerRecordObservation {

    protected final DefaultKafkaConsumerTelemetry.TelemetryContext context;
    protected final DefaultKafkaConsumerMetricsFactory.DefaultKafkaConsumerMetrics metrics;
    protected final Span span;
    protected final ConsumerRecord<?, ?> record;

    @Nullable
    private Throwable error;
    private long startedRecordHandle;

    public DefaultKafkaConsumerRecordObservation(DefaultKafkaConsumerTelemetry.TelemetryContext context,
                                                 DefaultKafkaConsumerMetricsFactory.DefaultKafkaConsumerMetrics metrics,
                                                 Span span,
                                                 ConsumerRecord<?, ?> record) {
        this.context = context;
        this.metrics = metrics;
        this.record = record;
        this.span = span;
    }

    @Override
    public void observeHandle() {
        this.startedRecordHandle = System.nanoTime();

        context.logger().atDebug()
            .addKeyValue("listenerName", context.listenerName())
            .addKeyValue("topic", record.topic())
            .addKeyValue("offset", record.offset())
            .addKeyValue("partition", record.partition())
            .log("KafkaListener starting handling record...");
    }

    @Override
    public Span span() {
        return this.span;
    }

    @Override
    public void end() {
        this.metrics.reportHandleRecordTook(record, startedRecordHandle, error);
        if (this.error == null) {
            this.span.setStatus(StatusCode.OK);

            context.logger().atDebug()
                .addKeyValue("listenerName", context.listenerName())
                .addKeyValue("topic", record.topic())
                .addKeyValue("offset", record.offset())
                .addKeyValue("partition", record.partition())
                .log("KafkaListener success record handled");
        } else {
            var errorValue = error.getClass().getCanonicalName();
            this.span.setAttribute(ErrorAttributes.ERROR_TYPE.getKey(), errorValue);

            context.logger().atWarn()
                .addKeyValue("listenerName", context.listenerName())
                .addKeyValue("topic", record.topic())
                .addKeyValue("offset", record.offset())
                .addKeyValue("partition", record.partition())
                .log("KafkaListener failed record handled due to: {}", error.getMessage());
        }
        this.span.end();
    }

    @Override
    public void observeError(Throwable e) {
        this.error = e;
        this.span.setStatus(StatusCode.ERROR);
        this.span.recordException(e);
    }
}
