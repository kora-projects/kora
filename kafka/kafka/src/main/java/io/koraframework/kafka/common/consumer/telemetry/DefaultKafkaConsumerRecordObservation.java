package io.koraframework.kafka.common.consumer.telemetry;

import io.koraframework.telemetry.common.TimerMeter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.semconv.ErrorAttributes;
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes;
import org.apache.kafka.clients.consumer.ConsumerRecord;

public class DefaultKafkaConsumerRecordObservation implements KafkaConsumerRecordObservation {

    protected final ConsumerRecord<?, ?> record;
    protected final Span span;
    protected final TimerMeter recordDurationMeter;

    private Throwable error;
    private long startedRecordHandle;

    public DefaultKafkaConsumerRecordObservation(ConsumerRecord<?, ?> record,
                                                 Span span,
                                                 TimerMeter recordDurationMeter) {
        this.record = record;
        this.span = span;
        this.recordDurationMeter = recordDurationMeter;
    }

    @Override
    public void observeHandle() {
        this.startedRecordHandle = System.nanoTime();
    }

    @Override
    public Span span() {
        return this.span;
    }

    @Override
    public void end() {
        var errorValue = error == null ? "" : error.getClass().getCanonicalName();
        this.recordDurationMeter.recordNanos(this.startedRecordHandle, () -> Tags.of(
            Tag.of(ErrorAttributes.ERROR_TYPE.getKey(), errorValue),
            Tag.of(MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME.getKey(), record.topic()),
            Tag.of(MessagingIncubatingAttributes.MESSAGING_DESTINATION_PARTITION_ID.getKey(), String.valueOf(record.partition()))
        ));

        if (this.error == null) {
            this.span.setStatus(StatusCode.OK);
        }
        this.span.setAttribute(ErrorAttributes.ERROR_TYPE.getKey(), errorValue);
        this.span.end();
    }

    @Override
    public void observeError(Throwable e) {
        this.error = e;
        this.span.setStatus(StatusCode.ERROR);
        this.span.recordException(e);
    }
}
