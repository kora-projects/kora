package ru.tinkoff.kora.http.client.async;

import ru.tinkoff.kora.http.common.HttpHeaders;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class AsyncHttpClientHeaders implements HttpHeaders {
    private final io.netty.handler.codec.http.HttpHeaders headers;

    public AsyncHttpClientHeaders(io.netty.handler.codec.http.HttpHeaders headers) {
        this.headers = headers;
    }

    @Nullable
    @Override
    public String getFirst(String name) {
        return this.headers.get(name);
    }

    @Override
    public List<String> get(String name) {
        return this.headers.getAll(name);
    }

    @Override
    public int size() {
        return this.headers.size();
    }

    @Nonnull
    @Override
    public Iterator<Map.Entry<String, List<String>>> iterator() {
        var i = this.headers.names().iterator();
        this.headers.getAll(i.next());

        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return i.hasNext();
            }

            @Override
            public Map.Entry<String, List<String>> next() {
                var key = i.next();
                var values = headers.getAll(key);
                return Map.entry(key, values);
            }
        };
    }
}
