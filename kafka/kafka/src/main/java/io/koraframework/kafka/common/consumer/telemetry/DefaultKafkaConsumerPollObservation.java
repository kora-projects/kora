package io.koraframework.kafka.common.consumer.telemetry;

import io.koraframework.logging.common.arg.StructuredArgumentWriter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.semconv.ErrorAttributes;
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static io.koraframework.kafka.common.consumer.telemetry.DefaultKafkaConsumerTelemetry.SYSTEM_IMPL;
import static io.koraframework.kafka.common.consumer.telemetry.DefaultKafkaConsumerTelemetry.SYSTEM_NAME;

public class DefaultKafkaConsumerPollObservation implements KafkaConsumerPollObservation {

    protected final DefaultKafkaConsumerTelemetry.TelemetryContext context;
    protected final DefaultKafkaConsumerMetricsFactory.DefaultKafkaConsumerMetrics metrics;
    protected final Span span;

    private ConsumerRecords<?, ?> records;
    private long startedRecordsHandle;
    @Nullable
    private Throwable error;

    public DefaultKafkaConsumerPollObservation(DefaultKafkaConsumerTelemetry.TelemetryContext context,
                                               DefaultKafkaConsumerMetricsFactory.DefaultKafkaConsumerMetrics metrics,
                                               Span span) {
        this.context = context;
        this.metrics = metrics;
        this.span = span;
    }

    @Override
    public void observeRecords(ConsumerRecords<?, ?> records) {
        this.startedRecordsHandle = System.nanoTime();
        this.records = records;
        this.span.setAttribute(MessagingIncubatingAttributes.MESSAGING_BATCH_MESSAGE_COUNT, (long) records.count());

        if (context.logger().isTraceEnabled()) {
            if (records.isEmpty()) {
                context.logger().atTrace()
                    .addKeyValue("listenerName", context.listenerName())
                    .log("{} polled '0' records", context.listenerName());
            } else {
                Map<String, List<Integer>> topicPartitionMap = new HashMap<>();
                for (TopicPartition partition : records.partitions()) {
                    topicPartitionMap.computeIfAbsent(partition.topic(), _ -> new ArrayList<>())
                        .add(partition.partition());
                }

                var arg = (StructuredArgumentWriter) gen -> {
                    gen.writeStartObject();
                    topicPartitionMap.forEach((topic, partitions) -> {
                        gen.writeName(topic);
                        gen.writeStartArray();
                        for (Integer partition : partitions) {
                            gen.writeNumber(partition);
                        }
                        gen.writeEndArray();
                    });
                    gen.writeEndObject();
                };

                context.logger().atTrace()
                    .addKeyValue("listenerName", context.listenerName())
                    .addKeyValue("topics", arg)
                    .log("KafkaListener polled '{}' records, starting handling records...", records.count());
            }
        } else if (!records.isEmpty()) {
            context.logger().atDebug()
                .addKeyValue("listenerName", context.listenerName())
                .log("KafkaListener polled '{}' records, starting handling records...", records.count());
        }
    }

    @Override
    public Span span() {
        return this.span;
    }

    @Override
    public void end() {
        this.metrics.reportHandleRecordsBatchTook(records, startedRecordsHandle, error);

        if (this.error == null) {
            this.span.setStatus(StatusCode.OK);
            this.context.logger().atInfo()
                .addKeyValue("listenerName", context.listenerName())
                .log("KafkaListener success '{}' records handled",
                    records.count());
        } else {
            var errorType = error.getClass().getCanonicalName();
            this.span.setAttribute(ErrorAttributes.ERROR_TYPE.getKey(), errorType);
            this.context.logger().atWarn()
                .addKeyValue("listenerName", context.listenerName())
                .log("KafkaListener failed '{}' records handling due to: {}",
                    records.count(), error.getMessage());
        }
        this.span.addEvent("result");
        this.span.end();
    }

    @Override
    public void observeError(Throwable e) {
        this.error = e;
        this.span.setStatus(StatusCode.ERROR);
    }

    @Override
    public KafkaConsumerRecordObservation observeRecord(ConsumerRecord<?, ?> record) {
        Span span;
        if (context.isTraceEnabled()) {
            span = createSpan(record).startSpan();
            this.span.addLink(span.getSpanContext());
        } else {
            span = Span.getInvalid();
        }

        return new DefaultKafkaConsumerRecordObservation(context, metrics, span, record);
    }

    protected SpanBuilder createSpan(ConsumerRecord<?, ?> record) {
        var root = Context.root();
        var parent = W3CTraceContextPropagator.getInstance().extract(root, record, ConsumerRecordTextMapGetter.INSTANCE);

        var spanBuilder = context.tracer()
            .spanBuilder(record.topic() + " process record")
            .setSpanKind(SpanKind.CONSUMER)
            .setParent(parent)
            .addLink(span.getSpanContext())
            .setAttribute(MessagingIncubatingAttributes.MESSAGING_SYSTEM, MessagingIncubatingAttributes.MessagingSystemIncubatingValues.KAFKA)
            .setAttribute(MessagingIncubatingAttributes.MESSAGING_CLIENT_ID.getKey(), context.clientId())
            .setAttribute(MessagingIncubatingAttributes.MESSAGING_CONSUMER_GROUP_NAME.getKey(), context.groupId())
            .setAttribute(MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME, record.topic())
            .setAttribute(MessagingIncubatingAttributes.MESSAGING_DESTINATION_PARTITION_ID, String.valueOf(record.partition()))
            .setAttribute(MessagingIncubatingAttributes.MESSAGING_KAFKA_OFFSET, record.offset())
            .setAttribute(SYSTEM_NAME, context.listenerName())
            .setAttribute(SYSTEM_IMPL, context.listenerImpl());
        try {
            spanBuilder.setAttribute(MessagingIncubatingAttributes.MESSAGING_KAFKA_MESSAGE_KEY, Objects.toString(record.key()));
        } catch (Exception ignore) {}
        return spanBuilder;
    }

    private enum ConsumerRecordTextMapGetter implements TextMapGetter<ConsumerRecord<?, ?>> {
        INSTANCE;

        @Override
        public Iterable<String> keys(ConsumerRecord<?, ?> carrier) {
            var set = new HashSet<String>();
            for (var header : carrier.headers()) {
                set.add(header.key());
            }
            return set;

        }

        @Override
        public String get(ConsumerRecord<?, ?> carrier, String key) {
            var header = carrier.headers().lastHeader(key);
            return header != null ? new String(header.value(), StandardCharsets.UTF_8) : null;
        }
    }
}
