package io.koraframework.kafka.common.consumer.telemetry.impl;

import io.koraframework.kafka.common.consumer.telemetry.KafkaConsumerTelemetryConfig;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OTel semconv messaging.kafka.message.key: "If the key type is not string, it's string representation has to be supplied
 * for the attribute. If the key has no unambiguous, canonical string form, don't include its value." A null key has no value.
 */
class KafkaRecordSpanMessageKeyTest {

    private static final AttributeKey<String> KEY = AttributeKey.stringKey("messaging.kafka.message.key");

    private final CopyOnWriteArrayList<SpanData> spans = new CopyOnWriteArrayList<>();
    private final DefaultKafkaConsumerTelemetry telemetry;

    KafkaRecordSpanMessageKeyTest() {
        var tracer = SdkTracerProvider.builder().addSpanProcessor(new SpanProcessor() {
            @Override public void onStart(Context parentContext, ReadWriteSpan span) {}
            @Override public boolean isStartRequired() { return false; }
            @Override public void onEnd(ReadableSpan span) { spans.add(span.toSpanData()); }
            @Override public boolean isEndRequired() { return true; }
        }).build().get("test");
        var config = new KafkaConsumerTelemetryConfig() {
            @Override public KafkaConsumerLoggingConfig logging() { return new KafkaConsumerLoggingConfig() {}; }
            @Override public KafkaConsumerMetricsConfig metrics() { return new KafkaConsumerMetricsConfig() {}; }
            @Override public KafkaConsumerTracingConfig tracing() { return new KafkaConsumerTracingConfig() {}; }
        };
        this.telemetry = new DefaultKafkaConsumerTelemetry("test", "foo.TestListener", config, tracer,
            DefaultKafkaConsumerTelemetryFactory.NOOP_METER_REGISTRY, DefaultKafkaConsumerMetricsFactory.INSTANCE,
            NoopKafkaConsumerLoggerFactory.INSTANCE, new Properties());
    }

    private String recordSpanKey(ConsumerRecord<?, ?> record) {
        var poll = telemetry.observePoll();
        poll.observeRecord(record).end();
        poll.end();
        return spans.stream().filter(s -> s.getName().startsWith("process ")).findFirst().orElseThrow().getAttributes().get(KEY);
    }

    @Test
    void nullKeyIsNotRecorded() {
        assertThat(recordSpanKey(new ConsumerRecord<>("topic", 0, 0L, null, "v"))).isNull();
    }

    @Test
    void byteArrayKeyIsNotRecordedAsArrayIdentity() {
        var key = recordSpanKey(new ConsumerRecord<>("topic", 0, 0L, "user-42".getBytes(StandardCharsets.UTF_8), "v".getBytes(StandardCharsets.UTF_8)));
        assertThat(key).isNull();
    }

    @Test
    void stringKeyIsRecorded() {
        assertThat(recordSpanKey(new ConsumerRecord<>("topic", 0, 0L, "user-42", "v"))).isEqualTo("user-42");
    }
}
