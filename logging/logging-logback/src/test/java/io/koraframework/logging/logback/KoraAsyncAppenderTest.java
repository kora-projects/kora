package io.koraframework.logging.logback;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.read.ListAppender;
import ch.qos.logback.core.status.Status;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Isolated
class KoraAsyncAppenderTest {

    private static final List<String> PROPERTIES = List.of(
        KoraAsyncAppender.QUEUE_SIZE_PROPERTY,
        KoraAsyncAppender.DISCARDING_THRESHOLD_PROPERTY,
        KoraAsyncAppender.MAX_FLUSH_TIME_PROPERTY,
        KoraAsyncAppender.NEVER_BLOCK_PROPERTY
    );

    @AfterEach
    void clearProperties() {
        PROPERTIES.forEach(System::clearProperty);
    }

    @Test
    void shouldUseKoraDefaults() {
        var appender = new KoraAsyncAppender();

        assertThat(appender.getQueueSize()).isEqualTo(512);
        assertThat(appender.getMaxFlushTime()).isEqualTo(1000);
        assertThat(appender.isNeverBlock()).isTrue();
        // left to Logback, which derives a fifth of the queue on start
        assertThat(appender.getDiscardingThreshold()).isEqualTo(-1);
    }

    @Test
    void shouldReadSettingsFromProperties() {
        System.setProperty(KoraAsyncAppender.QUEUE_SIZE_PROPERTY, "2048");
        System.setProperty(KoraAsyncAppender.DISCARDING_THRESHOLD_PROPERTY, "0");
        System.setProperty(KoraAsyncAppender.MAX_FLUSH_TIME_PROPERTY, "5s");
        System.setProperty(KoraAsyncAppender.NEVER_BLOCK_PROPERTY, "FALSE");

        var appender = new KoraAsyncAppender();

        assertThat(appender.getQueueSize()).isEqualTo(2048);
        assertThat(appender.getDiscardingThreshold()).isZero();
        assertThat(appender.getMaxFlushTime()).isEqualTo(5000);
        assertThat(appender.isNeverBlock()).isFalse();
    }

    @Test
    void shouldFallBackToDefaultsAndWarnOnInvalidValues() {
        System.setProperty(KoraAsyncAppender.QUEUE_SIZE_PROPERTY, "0");
        System.setProperty(KoraAsyncAppender.MAX_FLUSH_TIME_PROPERTY, "soon");
        System.setProperty(KoraAsyncAppender.NEVER_BLOCK_PROPERTY, "yes");
        var context = new LoggerContext();
        var appender = new KoraAsyncAppender();
        appender.setContext(context);
        appender.addAppender(new ListAppender<>());

        appender.start();

        try {
            assertThat(appender.getQueueSize()).isEqualTo(512);
            assertThat(appender.getMaxFlushTime()).isEqualTo(1000);
            assertThat(appender.isNeverBlock()).isTrue();
            assertThat(context.getStatusManager().getCopyOfStatusList())
                .filteredOn(status -> status.getLevel() == Status.WARN)
                .extracting(Status::getMessage)
                .anySatisfy(message -> assertThat(message).startsWith(KoraAsyncAppender.QUEUE_SIZE_PROPERTY + "=0"))
                .anySatisfy(message -> assertThat(message).startsWith(KoraAsyncAppender.MAX_FLUSH_TIME_PROPERTY + "=soon"))
                .anySatisfy(message -> assertThat(message).startsWith(KoraAsyncAppender.NEVER_BLOCK_PROPERTY + "=yes"));
        } finally {
            appender.stop();
        }
    }

    @Test
    void shouldLetConfigurationFileOverrideProperties() throws Exception {
        System.setProperty(KoraAsyncAppender.QUEUE_SIZE_PROPERTY, "2048");
        var xml = """
            <configuration>
                <appender name="LIST" class="ch.qos.logback.core.read.ListAppender"/>
                <appender name="ASYNC" class="io.koraframework.logging.logback.KoraAsyncAppender">
                    <queueSize>64</queueSize>
                    <neverBlock>false</neverBlock>
                    <appender-ref ref="LIST"/>
                </appender>
                <root level="INFO">
                    <appender-ref ref="ASYNC"/>
                </root>
            </configuration>
            """;
        var context = new LoggerContext();
        var configurator = new JoranConfigurator();
        configurator.setContext(context);
        configurator.doConfigure(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

        try {
            var appender = (KoraAsyncAppender) context.getLogger(Logger.ROOT_LOGGER_NAME).getAppender("ASYNC");

            assertThat(appender.getQueueSize()).isEqualTo(64);
            assertThat(appender.isNeverBlock()).isFalse();
            assertThat(appender.getMaxFlushTime()).isEqualTo(1000);
        } finally {
            context.stop();
        }
    }
}
