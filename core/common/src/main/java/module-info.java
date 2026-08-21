import org.jspecify.annotations.NullMarked;

@NullMarked
module kora.common {
    requires transitive kora.application.graph;
    requires transitive org.jspecify;
    requires transitive io.opentelemetry.api;
    requires transitive io.opentelemetry.context;
    requires transitive micrometer.core;
    requires transitive org.slf4j;
    requires static kotlin.stdlib;
    requires static kotlinx.coroutines.core;

    uses io.koraframework.common.context.ThreadContextPropagator;
    provides io.opentelemetry.context.ContextStorageProvider with io.koraframework.common.telemetry.OpentelemetryContextStorageProvider;
    exports io.koraframework.common;
    exports io.koraframework.common.annotation;
    exports io.koraframework.common.concurrent;
    exports io.koraframework.common.context;
    exports io.koraframework.common.liveness;
    exports io.koraframework.common.naming;
    exports io.koraframework.common.readiness;
    exports io.koraframework.common.telemetry;
    exports io.koraframework.common.util;
}
