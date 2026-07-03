import org.jspecify.annotations.NullMarked;

@NullMarked
module kora.logging.logback.json {
    requires transitive kora.common;
    requires transitive kora.logging.logback;
    requires transitive ch.qos.logback.classic;
    requires transitive ch.qos.logback.core;

    exports io.koraframework.logging.logback.json;
}
