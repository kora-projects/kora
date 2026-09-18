package io.koraframework.logging.logback.json;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import io.koraframework.logging.logback.text.ConsoleTextEncoderFactory;
import io.koraframework.logging.logback.text.ConsoleTextRecordEncoder;
import io.koraframework.logging.logback.KoraAsyncAppender;
import io.koraframework.logging.logback.KoraLogbackConfigurator;
import io.koraframework.logging.logback.KoraLogbackProperties;
import io.koraframework.logging.logback.LogbackEncoderFactory;
import io.koraframework.logging.logback.text.ColorConsoleTextEncoderFactory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.ServiceLoader;

import static org.assertj.core.api.Assertions.assertThat;

class KoraLogbackConfiguratorTest {

    private final KoraLogbackConfigurator configurator = new KoraLogbackConfigurator();

    @Test
    void shouldDiscoverAllEncoderFactoriesOnClasspath() {
        var factories = ServiceLoader.load(LogbackEncoderFactory.class).stream()
            .map(ServiceLoader.Provider::get)
            .map(LogbackEncoderFactory::name)
            .toList();

        assertThat(factories).contains(JsonEncoderFactory.NAME, ConsoleTextEncoderFactory.NAME,
            ColorConsoleTextEncoderFactory.NAME);
    }

    @Test
    void shouldDetectGradleTestWorker() {
        assertThat(KoraLogbackProperties.isRunningInTests()).isTrue();
    }

    @Test
    void shouldPreferPrettyEncoderWhenRunningInTests() {
        var selected = this.configurator.selectFactory(factories(), null);

        assertThat(selected).isInstanceOf(ColorConsoleTextEncoderFactory.class);
    }

    @Test
    void shouldPreferJsonOverTextWhenNothingIsSelected() {
        var selected = this.configurator.selectFactory(
            List.of(new ConsoleTextEncoderFactory(), new JsonEncoderFactory()), null);

        assertThat(selected).isInstanceOf(JsonEncoderFactory.class);
    }

    @Test
    void shouldSelectEncoderByName() {
        assertThat(this.configurator.selectFactory(factories(), "text")).isInstanceOf(ConsoleTextEncoderFactory.class);
        assertThat(this.configurator.selectFactory(factories(), "json")).isInstanceOf(JsonEncoderFactory.class);
        assertThat(this.configurator.selectFactory(factories(), "PRETTY")).isInstanceOf(ColorConsoleTextEncoderFactory.class);
    }

    @Test
    void shouldNotSelectUnknownEncoder() {
        assertThat(this.configurator.selectFactory(factories(), "unknown")).isNull();
    }

    @Test
    void shouldAttachSelectedEncoderToRootLogger() {
        var context = new LoggerContext();
        this.configurator.setContext(context);

        this.configurator.configureDefault(context, new JsonEncoderFactory());

        try {
            var root = context.getLogger(Logger.ROOT_LOGGER_NAME);
            var async = root.getAppender(KoraLogbackConfigurator.ASYNC_APPENDER_NAME);
            assertThat(async).isInstanceOf(KoraAsyncAppender.class);

            var console = ((KoraAsyncAppender) async).getAppender(KoraLogbackConfigurator.CONSOLE_APPENDER_NAME);
            assertThat(console).isInstanceOf(ConsoleAppender.class);

            @SuppressWarnings("unchecked")
            var encoder = ((ConsoleAppender<ILoggingEvent>) console).getEncoder();
            assertThat(encoder).isInstanceOf(JsonRecordEncoder.class);
            assertThat(encoder.isStarted()).isTrue();
        } finally {
            context.stop();
        }
    }

    @Test
    void shouldCreateTextEncoder() {
        var encoder = new ConsoleTextEncoderFactory().create(new LoggerContext());

        assertThat(encoder).isInstanceOf(ConsoleTextRecordEncoder.class);
        assertThat(encode(encoder)).doesNotContain("");
    }

    @Test
    void shouldCreateColoredTextEncoder() {
        var encoder = new ColorConsoleTextEncoderFactory().create(new LoggerContext());

        assertThat(encoder).isInstanceOf(ConsoleTextRecordEncoder.class);
        assertThat(encode(encoder)).contains("");
    }

    private static String encode(ch.qos.logback.core.encoder.Encoder<ILoggingEvent> encoder) {
        var event = new io.koraframework.logging.logback.KoraLoggingEvent(
            "thread", "logger", null, ch.qos.logback.classic.Level.INFO, "message", "message",
            null, null, null, java.util.Map.of(), 1000, 0, 1, null, java.util.Map.of(),
            io.opentelemetry.api.trace.SpanContext.getInvalid());
        return new String(encoder.encode(event), java.nio.charset.StandardCharsets.UTF_8);
    }

    private static List<LogbackEncoderFactory> factories() {
        return List.of(new ConsoleTextEncoderFactory(), new ColorConsoleTextEncoderFactory(), new JsonEncoderFactory());
    }
}
