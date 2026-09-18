package io.koraframework.logging.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.jul.LevelChangePropagator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class KoraLogbackJulBridgeTest {

    private final KoraLogbackConfigurator configurator = new KoraLogbackConfigurator();

    @BeforeEach
    void uninstallBridge() {
        // the global context of this JVM was configured by Kora already, so start from a JUL without the bridge
        SLF4JBridgeHandler.uninstall();
    }

    @AfterEach
    void restoreBridge() {
        System.clearProperty(KoraLogbackConfigurator.JUL_BRIDGE_PROPERTY);
        if (!SLF4JBridgeHandler.isInstalled()) {
            SLF4JBridgeHandler.install();
        }
    }

    @Test
    void shouldReplaceJulRootHandlersWithBridge() {
        var context = new LoggerContext();
        this.configurator.setContext(context);

        this.configurator.configureJulBridge(context);

        var handlers = java.util.logging.Logger.getLogger("").getHandlers();
        assertThat(handlers).hasSize(1).allMatch(handler -> handler instanceof SLF4JBridgeHandler);
    }

    @Test
    void shouldInstallOnlyOnceWhenCalledAgain() {
        var context = new LoggerContext();
        this.configurator.setContext(context);

        this.configurator.configureJulBridge(context);
        this.configurator.configureJulBridge(context);

        var handlers = java.util.logging.Logger.getLogger("").getHandlers();
        assertThat(Arrays.stream(handlers).filter(handler -> handler instanceof SLF4JBridgeHandler)).hasSize(1);
        assertThat(context.getCopyOfListenerList()).filteredOn(listener -> listener instanceof LevelChangePropagator).hasSize(1);
    }

    @Test
    void shouldPropagateLogbackLevelsToJul() {
        var context = new LoggerContext();
        this.configurator.setContext(context);
        this.configurator.configureJulBridge(context);

        context.getLogger("io.koraframework.jul.bridge.test").setLevel(Level.DEBUG);

        assertThat(java.util.logging.Logger.getLogger("io.koraframework.jul.bridge.test").getLevel())
            .isEqualTo(java.util.logging.Level.FINE);
    }

    @Test
    void shouldRouteJulRecordsIntoLogbackExactlyOnce() {
        var context = (LoggerContext) LoggerFactory.getILoggerFactory();
        this.configurator.setContext(context);
        this.configurator.configureJulBridge(context);
        this.configurator.configureJulBridge(context);

        var events = new ListAppender<ILoggingEvent>();
        events.setContext(context);
        events.start();
        var root = context.getLogger(Logger.ROOT_LOGGER_NAME);
        root.addAppender(events);
        try {
            java.util.logging.Logger.getLogger("io.koraframework.jul.bridge.routed").info("from jul");

            assertThat(events.list)
                .filteredOn(event -> event.getLoggerName().equals("io.koraframework.jul.bridge.routed"))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getFormattedMessage()).isEqualTo("from jul");
                    assertThat(event.getLevel()).isEqualTo(Level.INFO);
                });
        } finally {
            root.detachAppender(events);
        }
    }

    @Test
    void shouldLeaveJulAloneWhenDisabled() {
        System.setProperty(KoraLogbackConfigurator.JUL_BRIDGE_PROPERTY, "false");
        var context = new LoggerContext();
        this.configurator.setContext(context);

        this.configurator.configureJulBridge(context);

        assertThat(SLF4JBridgeHandler.isInstalled()).isFalse();
        assertThat(context.getCopyOfListenerList()).noneMatch(listener -> listener instanceof LevelChangePropagator);
    }
}
