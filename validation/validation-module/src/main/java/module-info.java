import org.jspecify.annotations.NullMarked;

@NullMarked
module kora.validation.module {
    requires transitive kora.validation.common;
    requires static transitive kora.http.server.common;

    exports io.koraframework.validation.module;
    exports io.koraframework.validation.module.http.server;
}
