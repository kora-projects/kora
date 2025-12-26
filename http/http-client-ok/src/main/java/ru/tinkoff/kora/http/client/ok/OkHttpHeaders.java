package ru.tinkoff.kora.http.client.ok;

import okhttp3.Headers;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import ru.tinkoff.kora.http.common.header.HttpHeaders;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

@NullMarked
public final class OkHttpHeaders implements HttpHeaders {
    private final Headers headers;

    public OkHttpHeaders(Headers headers) {
        this.headers = headers;
    }

    @Nullable
    @Override
    public String getFirst(String name) {
        return this.headers.get(name);
    }

    @Override
    public List<String> getAll(String name) {
        return this.headers.values(name);
    }

    @Override
    public boolean has(String key) {
        return this.headers.get(key) != null;
    }

    @Override
    public int size() {
        return this.headers.size();
    }

    @Override
    public Set<String> names() {
        return this.headers.names();
    }

    @Override
    public Iterator<Map.Entry<String, List<String>>> iterator() {
        var i = this.headers.names().iterator();
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return i.hasNext();
            }

            @Override
            public Map.Entry<String, List<String>> next() {
                var header = i.next();
                return Map.entry(header, headers.values(header));
            }
        };
    }
}
