import org.jspecify.annotations.NullMarked;

@NullMarked
module kora.http.server.common {
    requires transitive kora.http.common;
    requires transitive kora.logging.common;
    requires transitive kora.telemetry.common;

    exports ru.tinkoff.kora.http.server.common;
    exports ru.tinkoff.kora.http.server.common.annotation;
    exports ru.tinkoff.kora.http.server.common.auth;
    exports ru.tinkoff.kora.http.server.common.form;
    exports ru.tinkoff.kora.http.server.common.handler;
    exports ru.tinkoff.kora.http.server.common.router;
    exports ru.tinkoff.kora.http.server.common.telemetry;
}
