import org.jspecify.annotations.NullMarked;

@NullMarked
module kora.http.client.common {
    requires transitive kora.http.common;
    requires transitive kora.logging.common;
    requires transitive kora.telemetry.common;
    requires transitive kora.config.common;
    requires transitive java.net.http;

    exports io.koraframework.http.client.common;
    exports io.koraframework.http.client.common.annotation;
    exports io.koraframework.http.client.common.auth;
    exports io.koraframework.http.client.common.declarative;
    exports io.koraframework.http.client.common.exception;
    exports io.koraframework.http.client.common.interceptor;
    exports io.koraframework.http.client.common.request;
    exports io.koraframework.http.client.common.request.form;
    exports io.koraframework.http.client.common.request.mapper;
    exports io.koraframework.http.client.common.response;
    exports io.koraframework.http.client.common.response.mapper;
    exports io.koraframework.http.client.common.telemetry;
    exports io.koraframework.http.client.common.telemetry.impl;
}
