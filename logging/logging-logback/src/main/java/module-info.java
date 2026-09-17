import org.jspecify.annotations.NullMarked;

@NullMarked
module kora.logging.logback {
    requires transitive kora.common;
    requires transitive kora.logging.common;
    requires transitive ch.qos.logback.classic;
    requires transitive ch.qos.logback.core;

    exports io.koraframework.logging.logback;
    exports io.koraframework.logging.logback.text;
    exports io.koraframework.logging.logback.text.writer;

    uses io.koraframework.logging.logback.LogbackEncoderFactory;

    provides ch.qos.logback.classic.spi.Configurator with io.koraframework.logging.logback.KoraLogbackConfigurator;
    provides io.koraframework.logging.logback.LogbackEncoderFactory with
        io.koraframework.logging.logback.text.ConsoleTextEncoderFactory,
        io.koraframework.logging.logback.text.PrettyTextEncoderFactory;
}
