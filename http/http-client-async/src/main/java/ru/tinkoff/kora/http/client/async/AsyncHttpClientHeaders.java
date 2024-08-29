package ru.tinkoff.kora.http.client.async;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.http.common.header.AbstractHttpHeaders;
import ru.tinkoff.kora.http.common.header.HttpHeaders;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AsyncHttpClientHeaders extends AbstractHttpHeaders implements HttpHeaders {
    private final io.netty.handler.codec.http.HttpHeaders headers;

    public AsyncHttpClientHeaders(io.netty.handler.codec.http.HttpHeaders headers) {
        this.headers = headers;
    }

    @Nullable
    @Override
    public String getFirst(String headerName) {
        return this.headers.get(headerName);
    }

    @Override
    public List<String> getAll(String headerName) {
        return this.headers.getAll(headerName);
    }

    @Override
    public boolean has(String headerName) {
        return headers.contains(headerName);
    }

    @Override
    public int size() {
        return this.headers.size();
    }

    @Override
    public Set<String> names() {
        return headers.names();
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
                return Map.entry(key.toLowerCase(), values);
            }
        };
    }
}
