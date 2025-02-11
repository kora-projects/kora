module kora.s3_.client {
    requires transitive kora.config.common;
    requires transitive kora.http.client.common;
    requires transitive org.jetbrains.annotations;
    requires java.xml;

    exports ru.tinkoff.kora.s3.client;
    exports ru.tinkoff.kora.s3.client.annotation;
    exports ru.tinkoff.kora.s3.client.exception;
    exports ru.tinkoff.kora.s3.client.model;
    exports ru.tinkoff.kora.s3.client.telemetry;
}
