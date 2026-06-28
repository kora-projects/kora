import org.jspecify.annotations.NullMarked;

@NullMarked
module kora.database.common {
    requires transitive kora.common;
    requires transitive kora.telemetry.common;
    requires transitive kora.logging.common;
    requires transitive kora.config.common;

    exports io.koraframework.database.common;
    exports io.koraframework.database.common.annotation;
    exports io.koraframework.database.common.telemetry;
    exports io.koraframework.database.common.telemetry.impl;
}
