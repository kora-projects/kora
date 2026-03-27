import org.jspecify.annotations.NullMarked;

@NullMarked
module kora.http.server.common {
    requires transitive kora.http.common;
    requires transitive kora.logging.common;
    requires transitive kora.telemetry.common;

    exports io.koraframework.http.server.common;
    exports io.koraframework.http.server.common.annotation;
    exports io.koraframework.http.server.common.auth;
    exports io.koraframework.http.server.common.interceptor;
    exports io.koraframework.http.server.common.router;
    exports io.koraframework.http.server.common.telemetry;
    exports io.koraframework.http.server.common.system;
    exports io.koraframework.http.server.common.request;
    exports io.koraframework.http.server.common.request.mapper;
    exports io.koraframework.http.server.common.request.form;
    exports io.koraframework.http.server.common.response;
    exports io.koraframework.http.server.common.response.mapper;
}
