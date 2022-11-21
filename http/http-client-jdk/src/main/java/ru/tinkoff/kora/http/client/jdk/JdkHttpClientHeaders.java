package ru.tinkoff.kora.http.client.jdk;
;
import ru.tinkoff.kora.http.common.HttpHeaders;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class JdkHttpClientHeaders implements HttpHeaders {
    private final Map<String, List<String>> headers;

    public JdkHttpClientHeaders(java.net.http.HttpHeaders headers) {
        this.headers = headers.map();
    }

    @Nullable
    @Override
    public String getFirst(String name) {
        var headers = this.headers.get(name);
        if (headers == null || headers.isEmpty()) {
            return null;
        }
        return headers.get(0);
    }

    @Nullable
    @Override
    public List<String> get(String name) {
        return this.headers.get(name);
    }

    @Override
    public int size() {
        return this.headers.size();
    }

    @Nonnull
    @Override
    public Iterator<Map.Entry<String, List<String>>> iterator() {
        return this.headers.entrySet().iterator();
    }
}
