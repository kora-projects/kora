import org.jspecify.annotations.NullMarked;

@NullMarked
module kora.openfeature.common {
    requires transitive dev.openfeature.sdk;
    requires transitive kora.config.common;
    requires transitive kora.json.common;
    requires transitive kora.telemetry.common;
    requires kora.micrometer.common;

    exports io.koraframework.openfeature;
    exports io.koraframework.openfeature.annotation;
    exports io.koraframework.openfeature.context;
    exports io.koraframework.openfeature.event;
    exports io.koraframework.openfeature.json;
    exports io.koraframework.openfeature.mapper;
    exports io.koraframework.openfeature.telemetry;
    exports io.koraframework.openfeature.telemetry.impl;
}
