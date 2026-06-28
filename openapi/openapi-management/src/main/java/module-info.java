import org.jspecify.annotations.NullMarked;

@NullMarked
module kora.openapi.management {
    requires transitive kora.http.server.common;
    requires kora.config.common;

    exports io.koraframework.openapi.management;
}
