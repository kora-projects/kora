import org.jspecify.annotations.NullMarked;

@NullMarked
module kora.http.client.apache {
    requires transitive kora.http.client.common;
    requires transitive org.apache.httpcomponents.client5.httpclient5;
    requires transitive kora.config.common;

    exports io.koraframework.http.client.apache;
}
