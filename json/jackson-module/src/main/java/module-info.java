import org.jspecify.annotations.NullMarked;

@NullMarked
module kora.json.jackson.module {
    requires transitive kora.common;
    requires transitive kora.json.common;
    requires transitive tools.jackson.databind;
    requires static transitive kora.http.server.common;
    requires static transitive kora.http.client.common;

    exports io.koraframework.json.jackson.module;
    exports io.koraframework.json.jackson.module.http;
    exports io.koraframework.json.jackson.module.http.client;
    exports io.koraframework.json.jackson.module.http.server;
}
