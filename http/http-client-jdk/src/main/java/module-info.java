import org.jspecify.annotations.NullMarked;

@NullMarked
module kora.http.client.jdk {
    requires transitive java.net.http;
    requires transitive kora.http.client.common;
    requires transitive kora.config.common;

    exports io.koraframework.http.client.jdk;
}
