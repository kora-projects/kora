module kora.http.client.common {
    requires transitive kora.http.common;
    requires transitive kora.logging.common;
    requires transitive kora.telemetry.common;

    exports ru.tinkoff.kora.http.client.common;
    exports ru.tinkoff.kora.http.client.common.annotation;
    exports ru.tinkoff.kora.http.client.common.auth;
    exports ru.tinkoff.kora.http.client.common.declarative;
    exports ru.tinkoff.kora.http.client.common.form;
    exports ru.tinkoff.kora.http.client.common.interceptor;
    exports ru.tinkoff.kora.http.client.common.request;
    exports ru.tinkoff.kora.http.client.common.response;
    exports ru.tinkoff.kora.http.client.common.telemetry;
    exports ru.tinkoff.kora.http.client.common.writer;
}
