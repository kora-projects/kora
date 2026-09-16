package io.koraframework.logging.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import io.koraframework.logging.common.arg.StructuredArgument;
import io.opentelemetry.api.trace.SpanContext;
import org.junit.jupiter.api.Test;
import org.slf4j.Marker;
import org.slf4j.event.KeyValuePair;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ConsoleTextRecordEncoderTest {

    private static final String ANSI = "\\[[0-9;]*m";

    @Test
    void shouldWritePlainRecordWithoutEscapeCodes() {
        var encoded = encode(new ConsoleTextRecordEncoder(), event(Level.INFO));

        assertThat(encoded).doesNotContain("");
        assertThat(encoded).startsWith("1970-01-01 00:00:01.000 INFO  [test-thread] test.Logger - ");
        assertThat(encoded).contains("message formatted");
    }

    @Test
    void shouldWriteSameLayoutWhenColored() {
        var event = event(Level.INFO);

        var plain = encode(new ConsoleTextRecordEncoder(), event);
        var colored = encode(new ConsoleTextRecordEncoder(true), event);

        assertThat(colored).contains("");
        assertThat(colored.replaceAll(ANSI, "")).isEqualTo(plain);
    }

    @Test
    void shouldHighlightTimestampAndLevel() {
        var colored = encode(new ConsoleTextRecordEncoder(true), event(Level.ERROR));

        assertThat(colored).startsWith("[36m1970-01-01 00:00:01.000[0m ");
        assertThat(colored).contains("[1;31mERROR[0m [test-thread]");
    }

    @Test
    void shouldPadFourLetterLevelsTheSameWayWhenColored() {
        var plain = encode(new ConsoleTextRecordEncoder(), event(Level.WARN));
        var colored = encode(new ConsoleTextRecordEncoder(true), event(Level.WARN));

        assertThat(plain).contains("WARN  [test-thread]");
        assertThat(colored).contains("[31mWARN [0m [test-thread]");
    }

    private static String encode(ConsoleTextRecordEncoder encoder, ILoggingEvent event) {
        return new String(encoder.encode(event), StandardCharsets.UTF_8);
    }

    private static ILoggingEvent event(Level level) {
        return new KoraLoggingEvent(
            "test-thread",
            "test.Logger",
            null,
            level,
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
            SpanContext.getInvalid()
        );
    }
}
