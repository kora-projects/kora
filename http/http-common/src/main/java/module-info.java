import org.jspecify.annotations.NullMarked;

@NullMarked
module kora.http.common {
    requires transitive kora.common;

    requires static transitive kora.json.common;

    exports io.koraframework.http.common;
    exports io.koraframework.http.common.annotation;
    exports io.koraframework.http.common.auth;
    exports io.koraframework.http.common.body;
    exports io.koraframework.http.common.cookie;
    exports io.koraframework.http.common.form;
    exports io.koraframework.http.common.header;
    exports io.koraframework.http.common.telemetry;
}
