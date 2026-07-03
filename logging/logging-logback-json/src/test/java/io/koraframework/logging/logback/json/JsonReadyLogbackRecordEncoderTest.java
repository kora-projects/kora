package io.koraframework.logging.logback.json;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ThrowableProxy;
import io.koraframework.logging.common.arg.StructuredArgument;
import io.koraframework.logging.logback.KoraLoggingEvent;
import io.koraframework.logging.logback.json.writer.*;
import org.junit.jupiter.api.Test;
import org.slf4j.Marker;
import org.slf4j.event.KeyValuePair;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JsonReadyLogbackRecordEncoderTest {
    @Test
    void shouldWriteDefaultJsonFields() {
        var encoder = new JsonReadyLogbackRecordEncoder(List.of(
            new DefaultLoggingEventJsonWriter(),
            new DefaultExceptionJsonWriterLogging(),
            new DefaultStructuredJsonWriterLogging(),
            new DefaultTraceJsonWriterLogging()
        ));
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
    void shouldMergeMultipleDataArgumentsIntoSingleDataObjectAndNotDuplicateThemInAttributes() {
        var encoder = new JsonReadyLogbackRecordEncoder(List.of(new DefaultStructuredJsonWriterLogging()));
        var event = new KoraLoggingEvent(
            "test-thread",
            "test.Logger",
            null,
            Level.INFO,
            "message",
            "message",
            new Object[]{
                StructuredArgument.arg("data", gen -> {
                    gen.writeStartObject();
                    gen.writeStringProperty("second", "2");
                    gen.writeEndObject();
                }),
                StructuredArgument.arg("attribute", "value")
            },
            null,
            List.of((Marker) StructuredArgument.marker("data", gen -> {
                gen.writeStartObject();
                gen.writeStringProperty("first", "1");
                gen.writeEndObject();
            })),
            Map.of(),
            1000,
            0,
            1,
            null,
            Map.of(),
            io.opentelemetry.api.trace.SpanContext.getInvalid()
        );

        var json = new String(encoder.encode(event), StandardCharsets.UTF_8);

        assertThat(json).isEqualTo("{\"data\":{\"first\":\"1\",\"second\":\"2\"},\"attributes\":{\"attribute\":\"value\"}}\n");
    }

    @Test
    void shouldSkipBootstrapLogsAfterDisable() {
        var encoder = new JsonBootstrapLogbackRecordEncoder();
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
        var encoder = new JsonReadyLogbackRecordEncoder(writers);
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

    @Test
    void shouldMaskConfiguredFields() {
        var encoder = new JsonReadyLogbackRecordEncoder(
            List.of((gen, event) -> {
                gen.writeStringProperty("login", "user");
                gen.writeStringProperty("password", "secret");
                gen.writeName("nested");
                gen.writeStartObject();
                gen.writeStringProperty("token", "token-value");
                gen.writeStringProperty("visible", "value");
                gen.writeEndObject();
            }),
            new FieldLoggingEventJsonMasker(java.util.Set.of("password", "token"))
        );
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

        assertThat(json).isEqualTo("{\"login\":\"user\",\"password\":\"***\",\"nested\":{\"token\":\"***\",\"visible\":\"value\"}}\n");
    }

    @Test
    void shouldMaskWholeStructuredValues() {
        var encoder = new JsonReadyLogbackRecordEncoder(
            List.of((gen, event) -> {
                gen.writeName("credentials");
                gen.writeStartObject();
                gen.writeStringProperty("password", "secret");
                gen.writeEndObject();
                gen.writeStringProperty("visible", "value");
            }),
            new FieldLoggingEventJsonMasker(java.util.Set.of("credentials"))
        );
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

        assertThat(json).isEqualTo("{\"credentials\":\"***\",\"visible\":\"value\"}\n");
    }

    @Test
    void shouldWriteTypedKeyValuePairs() {
        var encoder = new JsonReadyLogbackRecordEncoder(List.of(new DefaultStructuredJsonWriterLogging()));
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
            List.of(
                new KeyValuePair("string", "value"),
                new KeyValuePair("int", 1),
                new KeyValuePair("long", 2L),
                new KeyValuePair("boolean", true),
                new KeyValuePair("double", 3.5d),
                new KeyValuePair("null", null)
            ),
            Map.of(),
            io.opentelemetry.api.trace.SpanContext.getInvalid()
        );

        var json = new String(encoder.encode(event), StandardCharsets.UTF_8);

        assertThat(json).isEqualTo("{\"attributes\":{\"string\":\"value\",\"int\":1,\"long\":2,\"boolean\":true,\"double\":3.5,\"null\":null}}\n");
    }

    @Test
    void shouldWriteExceptionStackTraceAndStructuredData() {
        var encoder = new JsonReadyLogbackRecordEncoder(List.of(new DefaultExceptionJsonWriterLogging()));
        var event = new KoraLoggingEvent(
            "test-thread",
            "test.Logger",
            null,
            Level.ERROR,
            "message",
            "message",
            null,
            new ThrowableProxy(new IllegalStateException("boom")),
            List.of((Marker) StructuredArgument.marker("exception", Map.of("code", "broken"))),
            Map.of(),
            1000,
            0,
            1,
            null,
            Map.of(),
            io.opentelemetry.api.trace.SpanContext.getInvalid()
        );

        var json = new String(encoder.encode(event), StandardCharsets.UTF_8);

        assertThat(json).contains("\"exception\":{");
        assertThat(json).contains("\"class\":\"java.lang.IllegalStateException\"");
        assertThat(json).contains("\"message\":\"boom\"");
        assertThat(json).contains("\"stackTrace\":\"java.lang.IllegalStateException: boom");
        assertThat(json).contains("\"data\":{\"code\":\"broken\"}");
    }
}
