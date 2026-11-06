package io.koraframework.logging.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.util.LogbackMDCAdapter;
import ch.qos.logback.core.AppenderBase;
import ch.qos.logback.core.read.ListAppender;
import ch.qos.logback.core.status.Status;
import io.koraframework.logging.common.arg.StructuredArgument;
import io.koraframework.logging.common.arg.StructuredArgumentWriter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

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
    void shouldDiscardInfoAndLowerEventsBelowDiscardingThreshold() {
        var list = new ListAppender<ILoggingEvent>();
        var appender = new KoraAsyncAppender();
        appender.setQueueSize(4);
        // above the queue size, so the remaining capacity is always below it
        appender.setDiscardingThreshold(5);

        logEveryLevel(appender, list);

        assertThat(list.list)
            .extracting(ILoggingEvent::getLevel)
            .containsExactly(Level.WARN, Level.ERROR);
    }

    @Test
    void shouldNotDiscardByLevelWhenThresholdIsZero() {
        var list = new ListAppender<ILoggingEvent>();
        var appender = new KoraAsyncAppender();
        appender.setDiscardingThreshold(0);

        logEveryLevel(appender, list);

        assertThat(list.list)
            .extracting(ILoggingEvent::getLevel)
            .containsExactly(Level.TRACE, Level.DEBUG, Level.INFO, Level.WARN, Level.ERROR);
    }

    @Test
    void shouldRenderStructuredArgumentsAsOfLoggingCall() {
        var context = new LoggerContext();
        context.setMDCAdapter(new LogbackMDCAdapter());
        var release = new CountDownLatch(1);
        var rendered = new CopyOnWriteArrayList<String>();
        // renders on the worker thread, as an encoder does, once the logging thread has moved on
        var downstream = new AppenderBase<ILoggingEvent>() {
            @Override
            protected void append(ILoggingEvent event) {
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                for (var marker : event.getMarkerList()) {
                    var argument = (StructuredArgument) marker;
                    rendered.add(argument.fieldName() + "=" + argument.writeToString());
                }
                for (var argument : event.getArgumentArray()) {
                    var structured = (StructuredArgument) argument;
                    rendered.add(structured.fieldName() + "=" + structured.writeToString());
                }
                for (var pair : event.getKeyValuePairs()) {
                    rendered.add(pair.key + "=" + ((StructuredArgumentWriter) pair.value).writeToString());
                }
            }
        };
        downstream.setContext(context);
        downstream.start();
        var appender = new KoraAsyncAppender();
        appender.setContext(context);
        appender.addAppender(downstream);
        appender.start();
        var logger = context.getLogger("test");
        logger.setLevel(Level.INFO);
        logger.setAdditive(false);
        logger.addAppender(appender);

        var items = new ArrayList<>(List.of("a", "b"));
        StructuredArgumentWriter writer = gen -> {
            gen.writeStartObject();
            gen.writeStringProperty("items", String.valueOf(items));
            gen.writeEndObject();
        };
        logger.atInfo()
            .addMarker(StructuredArgument.marker("data", writer))
            .addArgument(StructuredArgument.arg("argument", writer))
            .addKeyValue("pair", writer)
            .log("message {}");
        // the logged method goes on and changes what it logged
        items.clear();
        release.countDown();
        // flushes the queue
        appender.stop();

        assertThat(rendered).containsExactly(
            "data={\"items\":\"[a, b]\"}",
            "argument={\"items\":\"[a, b]\"}",
            "pair={\"items\":\"[a, b]\"}"
        );
    }

    private static void logEveryLevel(KoraAsyncAppender appender, ListAppender<ILoggingEvent> list) {
        var context = new LoggerContext();
        context.setMDCAdapter(new LogbackMDCAdapter());
        list.setContext(context);
        list.start();
        appender.setContext(context);
        appender.addAppender(list);
        appender.start();
        var logger = context.getLogger("test");
        logger.setLevel(Level.TRACE);
        logger.setAdditive(false);
        logger.addAppender(appender);

        logger.trace("trace");
        logger.debug("debug");
        logger.info("info");
        logger.warn("warn");
        logger.error("error");
        // flushes the queue
        appender.stop();
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
