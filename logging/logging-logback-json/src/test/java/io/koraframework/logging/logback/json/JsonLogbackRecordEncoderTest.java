package io.koraframework.logging.logback.json;

import ch.qos.logback.classic.Level;
import io.koraframework.logging.common.arg.StructuredArgument;
import io.koraframework.logging.logback.KoraLoggingEvent;
import org.junit.jupiter.api.Test;
import org.slf4j.Marker;
import org.slf4j.event.KeyValuePair;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JsonLogbackRecordEncoderTest {
    @Test
    void shouldWriteDefaultJsonFields() {
        var encoder = new JsonLogbackRecordEncoder(BootstrapWriters.DEFAULT);
        var event = new KoraLoggingEvent(
            "test-thread",
            "test.Logger",
            null,
            Level.INFO,
            "message {}",
            "message formatted",
            new Object[]{StructuredArgument.arg("attribute", "argument")},
            null,
            List.of((Marker) StructuredArgument.marker("data", "payload")),
            Map.of("slf4j", "value"),
            1000,
            0,
            1,
            List.of(new KeyValuePair("key", "value")),
            Map.of("kora", StructuredArgument.value(gen -> gen.writeNumber(42))),
            io.opentelemetry.api.trace.SpanContext.getInvalid()
        );

        var json = new String(encoder.encode(event), StandardCharsets.UTF_8);

        assertThat(json).contains("\"timestamp\":\"1970-01-01T00:00:01Z\"");
        assertThat(json).contains("\"level\":\"INFO\"");
        assertThat(json).contains("\"thread\":\"test-thread\"");
        assertThat(json).contains("\"logger\":\"test.Logger\"");
        assertThat(json).contains("\"message\":\"message formatted\"");
        assertThat(json).contains("\"mdc\":{\"kora\":42,\"slf4j\":\"value\"}");
        assertThat(json).contains("\"data\":\"payload\"");
        assertThat(json).contains("\"attributes\":{\"attribute\":\"argument\",\"key\":\"value\"}");
        assertThat(json).endsWith("\n");
    }

    @Test
    void shouldSkipBootstrapLogsAfterDisable() {
        var encoder = new BootstrapJsonLogbackRecordEncoder();
        var event = new KoraLoggingEvent(
            "test-thread",
            "test.Logger",
            null,
            Level.INFO,
            "message",
            "message",
            null,
            null,
            null,
            Map.of(),
            1000,
            0,
            1,
            null,
            Map.of(),
            io.opentelemetry.api.trace.SpanContext.getInvalid()
        );

        assertThat(encoder.encode(event)).isNotEmpty();

        encoder.disable();

        assertThat(encoder.encode(event)).isEmpty();
    }

    @Test
    void shouldKeepConfiguredWriterOrder() {
        var writers = new ArrayList<LoggingEventJsonWriter>();
        writers.add((gen, event) -> gen.writeStringProperty("b", "2"));
        writers.add((gen, event) -> gen.writeStringProperty("a", "1"));
        var encoder = new JsonLogbackRecordEncoder(writers);
        var event = new KoraLoggingEvent(
            "test-thread",
            "test.Logger",
            null,
            Level.INFO,
            "message",
            "message",
            null,
            null,
            null,
            Map.of(),
            1000,
            0,
            1,
            null,
            Map.of(),
            io.opentelemetry.api.trace.SpanContext.getInvalid()
        );

        var json = new String(encoder.encode(event), StandardCharsets.UTF_8);

        assertThat(json).isEqualTo("{\"b\":\"2\",\"a\":\"1\"}\n");
    }
}
