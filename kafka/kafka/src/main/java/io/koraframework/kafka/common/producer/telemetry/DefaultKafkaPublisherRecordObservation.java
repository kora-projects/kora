package io.koraframework.kafka.common.producer.telemetry;

import io.koraframework.common.telemetry.Observation;
import io.koraframework.common.telemetry.OpentelemetryContext;
import io.koraframework.logging.common.MDC;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.semconv.ErrorAttributes;
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class DefaultKafkaPublisherRecordObservation implements KafkaPublisherTelemetry.KafkaPublisherRecordObservation {

    protected final long start = System.nanoTime();
    protected final String publisherName;
    protected final KafkaPublisherTelemetryConfig config;
    protected final String topic;
    protected final Span span;
    protected final Meter.MeterProvider<Timer> durationProvider;
    protected final Meter.MeterProvider<Counter> sentMessagesProvider;
    protected final Logger logger;
    protected final MDC mdc;

    @Nullable
    protected RecordMetadata metadata;
    @Nullable
    protected Throwable error;

    public DefaultKafkaPublisherRecordObservation(String publisherName,
                                                  KafkaPublisherTelemetryConfig config,
                                                  String topic,
                                                  Span span,
                                                  Meter.MeterProvider<Timer> durationProvider,
                                                  Meter.MeterProvider<Counter> sentMessagesProvider,
                                                  Logger logger) {
        this.publisherName = publisherName;
        this.config = config;
        this.topic = topic;
        this.span = span;
        this.durationProvider = durationProvider;
        this.sentMessagesProvider = sentMessagesProvider;
        this.logger = logger;
        this.mdc = MDC.get().fork();
    }

    @Override
    public Span span() {
        return this.span;
    }

    @Override
    public void observeData(Object key, Object value) {

    }

    @Override
    public void observeRecord(ProducerRecord<byte[], byte[]> record) {
        logger.debug("KafkaPublisher '{}' sending record to topic {} and partition {}",
            publisherName, record.topic(), record.partition());
        W3CTraceContextPropagator.getInstance().inject(Context.root().with(span), record, ProducerRecordTextMapSetter.INSTANCE);
    }

    @Override
    public void onCompletion(RecordMetadata metadata, Exception exception) {
        ScopedValue.where(Observation.VALUE, this)
            .where(OpentelemetryContext.VALUE, span.storeInContext(Context.root()))
            .where(MDC.VALUE, this.mdc)
            .run(() -> {
                if (exception != null) {
                    this.observeError(exception);
                } else {
                    this.metadata = metadata;
                    var span = this.span;
                    span.setAttribute(MessagingIncubatingAttributes.MESSAGING_DESTINATION_PARTITION_ID, String.valueOf(metadata.partition()));
                    span.setAttribute(MessagingIncubatingAttributes.MESSAGING_KAFKA_OFFSET, metadata.offset());
                    span.setStatus(StatusCode.OK);
                }
                this.end();
            });
    }

    @Override
    public void observeError(Throwable e) {
        this.error = e;
        this.span.recordException(e);
        this.span.setStatus(StatusCode.ERROR);
    }

    @Override
    public void end() {
        if(!config.metrics().enabled()) {
            var took = System.nanoTime() - start;

            String errorValue = error == null ? "" : error.getClass().getCanonicalName();
            String partition = metadata == null ? "" : String.valueOf(metadata.partition());
            List<Tag> metricDynamicCacheKeyTags = new ArrayList<>(3); // + topic in key cache provider
            metricDynamicCacheKeyTags.add(Tag.of(ErrorAttributes.ERROR_TYPE.getKey(), errorValue));
            metricDynamicCacheKeyTags.add(Tag.of(MessagingIncubatingAttributes.MESSAGING_DESTINATION_PARTITION_ID.getKey(), partition));

            this.durationProvider.withTags(metricDynamicCacheKeyTags)
                .record(took, TimeUnit.NANOSECONDS);
            this.sentMessagesProvider.withTags(metricDynamicCacheKeyTags)
                .increment();
        }

        if (error != null) {
            logger.warn("KafkaPublisher '{}' error sending record to topic {}",
                publisherName, topic, error);
        } else {
            logger.debug("KafkaPublisher '{}' success sending record to topic {} and partition {} and offset {}",
                publisherName, topic, metadata.partition(), metadata.offset());
        }

        this.span.end();
    }

    private enum ProducerRecordTextMapSetter implements TextMapSetter<ProducerRecord<?, ?>> {
        INSTANCE;

        @Override
        public void set(ProducerRecord<?, ?> carrier, String key, String value) {
            carrier.headers().add(key, value.getBytes(StandardCharsets.UTF_8));
        }
    }
}
