import org.jspecify.annotations.NullMarked;

@NullMarked
module kora.http.server.common {
    requires transitive kora.http.common;
    requires transitive kora.logging.common;
    requires transitive kora.telemetry.common;

    exports io.koraframework.http.server.common;
    exports io.koraframework.http.server.common.annotation;
    exports io.koraframework.http.server.common.auth;
    exports io.koraframework.http.server.common.form;
    exports io.koraframework.http.server.common.handler;
    exports io.koraframework.http.server.common.mapper;
    exports io.koraframework.http.server.common.router;
    exports io.koraframework.http.server.common.telemetry;
}
