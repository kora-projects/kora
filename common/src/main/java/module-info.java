import org.jspecify.annotations.NullMarked;

@NullMarked
module kora.common {
    requires transitive kora.application.graph;
    requires transitive org.jspecify;
    requires transitive io.opentelemetry.api;
    requires transitive io.opentelemetry.context;
    requires transitive micrometer.core;
    requires transitive org.slf4j;

    provides io.opentelemetry.context.ContextStorageProvider with ru.tinkoff.kora.common.telemetry.OpentelemetryContextStorageProvider;
    exports ru.tinkoff.kora.common;
    exports ru.tinkoff.kora.common.annotation;
    exports ru.tinkoff.kora.common.liveness;
    exports ru.tinkoff.kora.common.naming;
    exports ru.tinkoff.kora.common.readiness;
    exports ru.tinkoff.kora.common.telemetry;
    exports ru.tinkoff.kora.common.util;
}
