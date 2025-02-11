module kora.common {
    requires transitive jakarta.annotation;
    requires transitive kora.application.graph;

    requires static org.reactivestreams;
    requires static reactor.core;
    requires static kotlinx.coroutines.core;

    exports ru.tinkoff.kora.common;
    exports ru.tinkoff.kora.common.annotation;
    exports ru.tinkoff.kora.common.readiness;
    exports ru.tinkoff.kora.common.naming;
    exports ru.tinkoff.kora.common.liveness;
    exports ru.tinkoff.kora.common.util;
    exports ru.tinkoff.kora.common.util.flow;
}
