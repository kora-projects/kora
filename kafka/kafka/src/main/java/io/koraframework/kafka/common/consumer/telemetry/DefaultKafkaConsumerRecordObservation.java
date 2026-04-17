package io.koraframework.kafka.common.consumer.telemetry;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.semconv.ErrorAttributes;
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.TimeUnit;

public class DefaultKafkaConsumerRecordObservation implements KafkaConsumerRecordObservation {

    protected final KafkaConsumerTelemetryConfig config;
    protected final ConsumerRecord<?, ?> record;
    protected final Span span;
    protected final Meter.MeterProvider<Timer> recordDurationMeter;

    @Nullable
    private Throwable error;
    private long startedRecordHandle;

    public DefaultKafkaConsumerRecordObservation(KafkaConsumerTelemetryConfig config,
                                                 ConsumerRecord<?, ?> record,
                                                 Span span,
                                                 Meter.MeterProvider<Timer> recordDurationMeter) {
        this.config = config;
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

        if (this.config.metrics().enabled()) {
            var took = System.nanoTime() - this.startedRecordHandle;
            var metricDynamicCacheKeyTags = Tags.of(
                    Tag.of(ErrorAttributes.ERROR_TYPE.getKey(), errorValue),
                    Tag.of(MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME.getKey(), record.topic()),
                    Tag.of(MessagingIncubatingAttributes.MESSAGING_DESTINATION_PARTITION_ID.getKey(), String.valueOf(record.partition())));

            this.recordDurationMeter.withTags(metricDynamicCacheKeyTags)
                    .record(took, TimeUnit.NANOSECONDS);
        }

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
