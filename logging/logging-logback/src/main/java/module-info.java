import org.jspecify.annotations.NullMarked;

@NullMarked
module kora.logging.logback {
    requires transitive kora.common;
    requires transitive kora.logging.common;
    requires transitive ch.qos.logback.classic;
    requires transitive ch.qos.logback.core;

    exports io.koraframework.logging.logback;

    provides ch.qos.logback.classic.spi.Configurator with io.koraframework.logging.logback.ConsoleTextLogbackConfigurator;
}
