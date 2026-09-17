package io.koraframework.logging.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import io.koraframework.logging.common.arg.StructuredArgument;
import io.koraframework.logging.logback.writer.DefaultMessageTextWriter;
import io.koraframework.logging.logback.writer.DefaultMdcTextWriter;
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

    @Test
    void shouldAssembleRecordFromConfiguredWriters() {
        var encoder = new ConsoleTextRecordEncoder(List.of(
            (out, e) -> out.append("custom "),
            new DefaultMessageTextWriter()
        ));

        assertThat(encode(encoder, event(Level.INFO))).isEqualTo("custom message formatted\n");
    }

    @Test
    void shouldReplaceDefaultWritersOnFirstAddedWriter() {
        var encoder = new ConsoleTextRecordEncoder();
        encoder.addWriter(new DefaultMdcTextWriter());
        encoder.addWriter(new DefaultMessageTextWriter());

        assertThat(encode(encoder, event(Level.INFO))).isEqualTo("kora=42 slf4j=value message formatted\n");
    }

    @Test
    void shouldConfigureWritersFromLogbackConfigurationFile() throws Exception {
        var xml = """
            <configuration>
                <appender name="TEXT" class="ch.qos.logback.core.ConsoleAppender">
                    <encoder class="io.koraframework.logging.logback.ConsoleTextRecordEncoder">
                        <writer class="io.koraframework.logging.logback.writer.DefaultMdcTextWriter"/>
                        <writer class="io.koraframework.logging.logback.writer.DefaultMessageTextWriter"/>
                    </encoder>
                </appender>
                <root level="INFO">
                    <appender-ref ref="TEXT"/>
                </root>
            </configuration>
            """;
        var context = new ch.qos.logback.classic.LoggerContext();
        var configurator = new ch.qos.logback.classic.joran.JoranConfigurator();
        configurator.setContext(context);
        configurator.doConfigure(new java.io.ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

        try {
            @SuppressWarnings("unchecked")
            var appender = (ch.qos.logback.core.ConsoleAppender<ILoggingEvent>) context
                .getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME)
                .getAppender("TEXT");

            assertThat(new String(appender.getEncoder().encode(event(Level.INFO)), StandardCharsets.UTF_8))
                .isEqualTo("kora=42 slf4j=value message formatted\n");
        } finally {
            context.stop();
        }
    }

    @Test
    void shouldKeepRecordWhenWriterFails() {
        var encoder = new ConsoleTextRecordEncoder(List.of(
            new DefaultMessageTextWriter(),
            (out, e) -> {
                throw new IllegalStateException("broken");
            }
        ));

        var encoded = encode(encoder, event(Level.INFO));

        assertThat(encoded).startsWith("INFO test.Logger - message formatted <text encoding failed: ");
        assertThat(encoded).contains("broken").endsWith(">\n");
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
