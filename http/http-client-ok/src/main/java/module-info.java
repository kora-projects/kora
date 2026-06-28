import org.jspecify.annotations.NullMarked;

@NullMarked
module kora.http.client.ok {
    requires transitive kora.http.client.common;
    requires transitive okhttp3;
    requires transitive okio;
    requires transitive kora.config.common;

    exports io.koraframework.http.client.ok;
}
