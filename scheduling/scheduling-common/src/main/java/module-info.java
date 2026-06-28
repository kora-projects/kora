import org.jspecify.annotations.NullMarked;

@NullMarked
module kora.scheduling.common {
    requires transitive kora.common;
    requires transitive kora.telemetry.common;
    requires transitive kora.logging.common;
    requires transitive kora.config.common;

    exports io.koraframework.scheduling.common;
    exports io.koraframework.scheduling.common.telemetry;
    exports io.koraframework.scheduling.common.telemetry.impl;
}
