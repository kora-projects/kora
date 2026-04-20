package io.koraframework.kafka.common.producer.telemetry;

import io.koraframework.common.telemetry.Observation;
import io.koraframework.common.telemetry.OpentelemetryContext;
import io.koraframework.kafka.common.producer.telemetry.DefaultKafkaPublisherMetricsFactory.DefaultKafkaPublisherMetrics;
import io.koraframework.kafka.common.producer.telemetry.DefaultKafkaPublisherTelemetry.TelemetryContext;
import io.koraframework.logging.common.MDC;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;

public class DefaultKafkaPublisherRecordObservation implements KafkaPublisherTelemetry.KafkaPublisherRecordObservation {

    protected final long startedRecordSend = System.nanoTime();

    protected final TelemetryContext context;
    protected final DefaultKafkaPublisherMetrics metrics;
    protected final String topic;
    protected final Span span;
    protected final MDC mdc;

    @Nullable
    protected Object key;
    protected Object value;
    @Nullable
    protected ProducerRecord<byte[], byte[]> record;
    @Nullable
    protected RecordMetadata metadata;
    @Nullable
    protected Throwable error;

    public DefaultKafkaPublisherRecordObservation(TelemetryContext context,
                                                  DefaultKafkaPublisherMetrics metrics,
                                                  String topic,
                                                  Span span) {
        this.context = context;
        this.metrics = metrics;
        this.topic = topic;
        this.span = span;
        this.mdc = MDC.get().fork();
    }

    @Override
    public Span span() {
        return this.span;
    }

    @Override
    public void observeData(Object key, Object value) {
        this.key = key;
        this.value = value;
    }

    @Override
    public void observeRecord(ProducerRecord<byte[], byte[]> record) {
        this.record = record;
        this.context.logger()
            .atDebug()
            .addKeyValue("topic", record.topic())
            .addKeyValue("publisherName", context.publisherName())
            .log("KafkaPublisher starting record sending...");
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
        this.metrics.reportHandleRecordTook(topic, key, value, record, metadata, error, startedRecordSend);
        if (error != null) {
            var errorType = error.getClass().getCanonicalName();
            context.logger().atWarn()
                .addKeyValue("errorType", errorType)
                .addKeyValue("topic", topic)
                .addKeyValue("publisherName", context.publisherName())
                .log("KafkaPublisher failed record sending due to: {}", error.getMessage());
        } else if (metadata != null) {
            context.logger().atInfo()
                .addKeyValue("topic", metadata.topic())
                .addKeyValue("partition", metadata.partition())
                .addKeyValue("offset", metadata.offset())
                .addKeyValue("publisherName", context.publisherName())
                .log("KafkaPublisher success record sent");
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
