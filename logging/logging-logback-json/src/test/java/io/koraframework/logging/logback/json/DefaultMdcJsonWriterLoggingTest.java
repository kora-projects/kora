package io.koraframework.logging.logback.json;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import io.koraframework.logging.common.arg.StructuredArgument;
import io.koraframework.logging.common.arg.StructuredArgumentWriter;
import io.koraframework.logging.logback.KoraLoggingEvent;
import io.koraframework.logging.logback.json.writer.DefaultMdcJsonWriterLogging;
import io.koraframework.logging.logback.json.writer.DefaultStructuredJsonWriterLogging;
import io.opentelemetry.api.trace.SpanContext;
import org.junit.jupiter.api.Test;
import org.slf4j.Marker;
import org.slf4j.event.KeyValuePair;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultMdcJsonWriterLoggingTest {

    @Test
    void shouldWriteOnlyMdc() {
        var encoder = new JsonRecordEncoder(List.of(new DefaultMdcJsonWriterLogging()));

        var json = encode(encoder, event(
            Map.of("slf4j", "value"),
            Map.of("kora", StructuredArgument.value(gen -> gen.writeNumber(42)))));

        assertThat(json).isEqualTo("{\"mdc\":{\"kora\":42,\"slf4j\":\"value\"}}\n");
    }

    @Test
    void shouldPreferKoraMdcOverSlf4jMdcOnConflictingKeys() {
        var encoder = new JsonRecordEncoder(List.of(new DefaultMdcJsonWriterLogging()));

        var json = encode(encoder, event(
            Map.of("key", "slf4j"),
            Map.of("key", StructuredArgument.value(gen -> gen.writeString("kora")))));

        assertThat(json).isEqualTo("{\"mdc\":{\"key\":\"kora\"}}\n");
    }

    @Test
    void shouldWriteNothingWhenBothMdcAreEmpty() {
        var encoder = new JsonRecordEncoder(List.of(new DefaultMdcJsonWriterLogging()));

        var json = encode(encoder, event(Map.of(), Map.of()));

        assertThat(json).isEqualTo("{}\n");
    }

    @Test
    void shouldNotWriteMdcFromStructuredWriter() {
        var encoder = new JsonRecordEncoder(List.of(new DefaultStructuredJsonWriterLogging()));

        var json = encode(encoder, event(
            Map.of("slf4j", "value"),
            Map.of("kora", StructuredArgument.value(gen -> gen.writeNumber(42)))));

        assertThat(json).doesNotContain("mdc");
        assertThat(json).isEqualTo("{\"data\":\"payload\",\"args\":{\"attribute\":\"argument\",\"key\":\"value\"}}\n");
    }

    private static String encode(JsonRecordEncoder encoder, ILoggingEvent event) {
        return new String(encoder.encode(event), StandardCharsets.UTF_8);
    }

    private static ILoggingEvent event(Map<String, String> slf4jMdc, Map<String, StructuredArgumentWriter> koraMdc) {
        return new KoraLoggingEvent(
            "test-thread",
            "test.Logger",
            null,
            Level.INFO,
            "message",
            "message",
            new Object[]{StructuredArgument.arg("attribute", "argument")},
            null,
            List.of((Marker) StructuredArgument.marker("data", "payload")),
            slf4jMdc,
            1000,
            0,
            1,
            List.of(new KeyValuePair("key", "value")),
            koraMdc,
            SpanContext.getInvalid()
        );
    }
}
