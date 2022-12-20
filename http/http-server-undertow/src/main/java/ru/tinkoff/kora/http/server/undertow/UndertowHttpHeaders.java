package ru.tinkoff.kora.http.server.undertow;

import io.undertow.util.HeaderMap;
import ru.tinkoff.kora.http.common.HttpHeaders;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class UndertowHttpHeaders implements HttpHeaders {
    private final HeaderMap headerMap;

    public UndertowHttpHeaders(HeaderMap headerMap) {
        this.headerMap = headerMap;
    }

    @Nullable
    @Override
    public String getFirst(String name) {
        return this.headerMap.getFirst(name);
    }

    @Override
    public List<String> get(String name) {
        var headers = this.headerMap.get(name);
        if (headers == null) {
            return null;
        }
        return List.copyOf(headers);
    }

    @Override
    public boolean has(String key) {
        return headerMap.contains(key);
    }

    @Override
    public int size() {
        return this.headerMap.size();
    }

    @Nonnull
    @Override
    public Iterator<Map.Entry<String, List<String>>> iterator() {
        var i = this.headerMap.iterator();
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return i.hasNext();
            }

            @Override
            public Map.Entry<String, List<String>> next() {
                var next = i.next();
                return Map.entry(next.getHeaderName().toString(), next);
            }
        };
    }
}
