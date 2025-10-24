package ru.tinkoff.kora.kafka.common.producer.telemetry;

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
import org.slf4j.Logger;
import ru.tinkoff.kora.common.telemetry.Observation;
import ru.tinkoff.kora.common.telemetry.OpentelemetryContext;
import ru.tinkoff.kora.logging.common.MDC;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class DefaultKafkaPublisherRecordObservation implements KafkaPublisherTelemetry.KafkaPublisherRecordObservation {

    protected final long start = System.nanoTime();
    protected ProducerRecord<byte[], byte[]> record;
    protected final Span span;
    protected final Meter.MeterProvider<Timer> durationProvider;
    protected final Meter.MeterProvider<Counter> sentMessagesProvider;
    protected final Logger logger;
    protected final MDC mdc;
    protected RecordMetadata metadata;
    protected Throwable error;

    public DefaultKafkaPublisherRecordObservation(Span span, Meter.MeterProvider<Timer> durationProvider, Meter.MeterProvider<Counter> sentMessagesProvider, Logger logger) {
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
        logger.debug("Kafka Producer sending record to topic {} and partition {}", record.topic(), record.partition());
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
        var took = System.nanoTime() - start;
        var tags = List.of(
            Tag.of(ErrorAttributes.ERROR_TYPE.getKey(), error == null ? "" : error.getClass().getCanonicalName()),
            Tag.of(MessagingIncubatingAttributes.MESSAGING_DESTINATION_PARTITION_ID.getKey(), metadata == null ? "" : ("" + metadata.partition()))
        );

        this.durationProvider.withTags(tags).record(took, TimeUnit.NANOSECONDS);
        this.sentMessagesProvider.withTags(tags).increment();

        if (error != null) {
            logger.warn("Kafka Producer error sending record to topic {} and partition {}", record.topic(), record.partition(), error);
        } else {
            logger.debug("Kafka Producer success sending record to topic {} and partition {} and offset {}", metadata.topic(), metadata.partition(), metadata.offset());
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
