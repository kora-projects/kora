package io.koraframework.logging.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.util.LogbackMDCAdapter;
import ch.qos.logback.core.CoreConstants;
import ch.qos.logback.core.OutputStreamAppender;
import ch.qos.logback.core.pattern.DynamicConverter;
import io.koraframework.logging.common.MDC;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class KoraMdcConverterTest {

    @Test
    void rendersNothingOnThreadWithoutMdc() {
        var layout = layout();

        assertThat(layout.doLayout(event("started"))).isEqualTo("started");
    }

    @Test
    void synchronousAppenderKeepsLinesLoggedOutsideMdcScope() {
        var context = new LoggerContext();
        context.setMDCAdapter(new LogbackMDCAdapter());
        var rules = new HashMap<String, Supplier<DynamicConverter<?>>>();
        rules.put("koraMdc", KoraMdcConverter::new);
        context.putObject(CoreConstants.PATTERN_RULE_REGISTRY_FOR_SUPPLIERS, rules);
        var encoder = new PatternLayoutEncoder();
        encoder.setContext(context);
        encoder.setPattern("%koraMdc%msg%n");
        encoder.start();
        var out = new ByteArrayOutputStream();
        var appender = new OutputStreamAppender<ILoggingEvent>();
        appender.setContext(context);
        appender.setEncoder(encoder);
        appender.setOutputStream(out);
        appender.start();
        var logger = context.getLogger("test");
        logger.setAdditive(false);
        logger.addAppender(appender);

        logger.info("application started");

        assertThat(out.toString(StandardCharsets.UTF_8)).isEqualTo("application started" + System.lineSeparator());
    }

    @Test
    void rendersBoundMdc() {
        var layout = layout();
        var mdc = new MDC();
        mdc.put0("requestId", "42");

        var line = ScopedValue.where(MDC.VALUE, mdc).call(() -> layout.doLayout(event("handled")));

        assertThat(line).isEqualTo("requestId: \"42\" handled");
    }

    private static PatternLayout layout() {
        var layout = new PatternLayout();
        layout.setContext(new LoggerContext());
        layout.getInstanceConverterMap().put("koraMdc", KoraMdcConverter::new);
        layout.setPattern("%koraMdc%msg");
        layout.start();
        return layout;
    }

    private static LoggingEvent event(String message) {
        var context = new LoggerContext();
        return new LoggingEvent(KoraMdcConverterTest.class.getName(), context.getLogger("test"), Level.INFO, message, null, null);
    }
}
