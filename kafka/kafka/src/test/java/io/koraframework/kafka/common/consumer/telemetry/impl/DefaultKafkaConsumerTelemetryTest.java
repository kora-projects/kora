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

import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultKafkaConsumerTelemetryTest {

    @Test
    void tracingAttributesAreAddedToPollAndRecordSpans() {
        var spans = new CopyOnWriteArrayList<SpanData>();
        var tracer = SdkTracerProvider.builder().addSpanProcessor(new SpanProcessor() {
            @Override public void onStart(Context parentContext, ReadWriteSpan span) {}
            @Override public boolean isStartRequired() { return false; }
            @Override public void onEnd(ReadableSpan span) { spans.add(span.toSpanData()); }
            @Override public boolean isEndRequired() { return true; }
        }).build().get("test");
        var config = new KafkaConsumerTelemetryConfig() {
            @Override public KafkaConsumerLoggingConfig logging() { return new KafkaConsumerLoggingConfig() {}; }
            @Override public KafkaConsumerMetricsConfig metrics() { return new KafkaConsumerMetricsConfig() {}; }
            @Override public KafkaConsumerTracingConfig tracing() {
                return new KafkaConsumerTracingConfig() {
                    @Override public Map<String, String> attributes() { return Map.of("team", "payments"); }
                };
            }
        };
        var telemetry = new DefaultKafkaConsumerTelemetry("test", "foo.TestListener", config, tracer,
            DefaultKafkaConsumerTelemetryFactory.NOOP_METER_REGISTRY, DefaultKafkaConsumerMetricsFactory.INSTANCE,
            NoopKafkaConsumerLoggerFactory.INSTANCE, new Properties());

        var poll = telemetry.observePoll();
        poll.observeRecord(new ConsumerRecord<>("topic", 0, 0L, "k", "v")).end();
        poll.end();

        var team = AttributeKey.stringKey("team");
        assertThat(spans).extracting(SpanData::getName).containsExactlyInAnyOrder("poll", "process topic");
        for (var span : spans) {
            assertThat(span.getAttributes().get(team)).as("attribute of span '%s'", span.getName()).isEqualTo("payments");
        }
    }
}
