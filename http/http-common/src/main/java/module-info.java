import org.jspecify.annotations.NullMarked;

@NullMarked
module kora.http.common {
    requires transitive kora.common;

    requires static transitive kora.json.common;

    exports ru.tinkoff.kora.http.common;
    exports ru.tinkoff.kora.http.common.annotation;
    exports ru.tinkoff.kora.http.common.auth;
    exports ru.tinkoff.kora.http.common.body;
    exports ru.tinkoff.kora.http.common.cookie;
    exports ru.tinkoff.kora.http.common.form;
    exports ru.tinkoff.kora.http.common.header;
    exports ru.tinkoff.kora.http.common.telemetry;
}
