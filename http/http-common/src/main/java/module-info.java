module kora.http.common {
    requires transitive kora.common;
    requires transitive org.slf4j;
    requires transitive java.net.http;

    exports ru.tinkoff.kora.http.common;
    exports ru.tinkoff.kora.http.common.annotation;
    exports ru.tinkoff.kora.http.common.auth;
    exports ru.tinkoff.kora.http.common.body;
    exports ru.tinkoff.kora.http.common.cookie;
    exports ru.tinkoff.kora.http.common.form;
    exports ru.tinkoff.kora.http.common.header;
}
